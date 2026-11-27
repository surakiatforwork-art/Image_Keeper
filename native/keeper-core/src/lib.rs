#![cfg_attr(not(test), no_std)]
//! Pixel-exact EXIF orientation, optional horizontal mirror, and centered crop.
//! Android decoding, resizing, color management and JPEG compression remain in Kotlin.
#[derive(Clone, Copy)]
struct Plan { width: usize, height: usize, orientation: u32, mirror: bool }
impl Plan {
    fn dimensions(self) -> (usize, usize) {
        if (5..=8).contains(&self.orientation) { (self.height, self.width) }
        else { (self.width, self.height) }
    }
    fn source(self, x: usize, y: usize) -> (usize, usize) {
        let (ow, _) = self.dimensions();
        let x = if self.mirror { ow - 1 - x } else { x };
        let (w,h) = (self.width,self.height);
        match self.orientation {
            2 => (w-1-x,y), 3 => (w-1-x,h-1-y), 4 => (x,h-1-y),
            5 => (y,x), 6 => (y,h-1-x), 7 => (w-1-y,h-1-x),
            8 => (w-1-y,x), _ => (x,y),
        }
    }
}
#[cfg(target_os="android")]
mod android {
    use super::Plan;
    use core::{ffi::c_void, ptr};
    type Obj = *mut c_void;
    #[repr(C)]
    #[derive(Default)]
    struct Info { width: u32, height: u32, stride: u32, format: i32, flags: u32 }
    #[link(name="jnigraphics")]
    extern "C" {
        fn AndroidBitmap_getInfo(env: Obj, bitmap: Obj, info: *mut Info) -> i32;
        fn AndroidBitmap_lockPixels(env: Obj, bitmap: Obj, pixels: *mut Obj) -> i32;
        fn AndroidBitmap_unlockPixels(env: Obj, bitmap: Obj) -> i32;
    }
    struct Locked { env: Obj, bitmap: Obj, pixels: Obj }
    impl Locked {
        unsafe fn new(env: Obj, bitmap: Obj) -> Option<Self> {
            let mut pixels = ptr::null_mut();
            if AndroidBitmap_lockPixels(env, bitmap, &mut pixels) != 0 { return None; }
            let locked = Self { env, bitmap, pixels };
            if pixels.is_null() { return None; }
            Some(locked)
        }
    }
    impl Drop for Locked {
        fn drop(&mut self) { unsafe { AndroidBitmap_unlockPixels(self.env, self.bitmap); } }
    }
    fn valid(i: &Info) -> bool {
        i.format == 1 && i.width > 0 && i.height > 0 &&
        i.width <= 32768 && i.height <= 32768 &&
        i.stride as u64 >= i.width as u64 * 4 &&
        (i.stride as u64)*(i.height as u64) <= isize::MAX as u64
    }
    #[no_mangle]
    pub unsafe extern "system" fn Java_com_branchphotovault_util_RustImageEngine_transformNative(
        env: Obj, _class: Obj, source: Obj, dest: Obj, orientation: i32,
        mirror: u8, crop_x: i32, crop_y: i32
    ) -> u8 {
        if source.is_null() || dest.is_null() || source == dest ||
            !(1..=8).contains(&orientation) || crop_x < 0 || crop_y < 0 { return 0; }
        let (mut si,mut di) = (Info::default(),Info::default());
        if AndroidBitmap_getInfo(env, source, &mut si) != 0 ||
            AndroidBitmap_getInfo(env, dest, &mut di) != 0 ||
            !valid(&si) || !valid(&di) { return 0; }
        let plan = Plan { width: si.width as usize, height: si.height as usize,
            orientation: orientation as u32, mirror: mirror != 0 };
        let (ow,oh) = plan.dimensions();
        let (x0,y0,dw,dh) = (crop_x as usize,crop_y as usize,di.width as usize,di.height as usize);
        if x0+dw>ow || y0+dh>oh { return 0; }
        let Some(src) = Locked::new(env,source) else { return 0; };
        let Some(dst) = Locked::new(env,dest) else { return 0; };
        let (ss,ds) = (si.stride as usize,di.stride as usize);
        let a = src.pixels.cast::<u8>(); let b = dst.pixels.cast::<u8>();
        for y in 0..dh {
            let (sx,sy) = plan.source(x0,y0+y);
            let first = sy*ss+sx*4;
            let out = b.add(y*ds);
            if dw == 1 {
                ptr::copy_nonoverlapping(a.add(first),out,4);
                continue;
            }
            let (nx,ny) = plan.source(x0+1,y0+y);
            let step = (ny*ss+nx*4) as isize - first as isize;
            if step == 4 {
                ptr::copy_nonoverlapping(a.add(first),out,dw*4);
            } else {
                for x in 0..dw {
                    let offset = first as isize + x as isize*step;
                    ptr::copy_nonoverlapping(a.offset(offset),out.add(x*4),4);
                }
            }
        }
        1
    }
}
#[cfg(not(test))]
#[panic_handler]
fn panic(_info: &core::panic::PanicInfo) -> ! {
    #[link(name="c")] extern "C" { fn abort() -> !; }
    unsafe { abort() }
}
#[cfg(test)]
mod tests {
    use super::Plan;
    #[test]
    fn all_exif_orientations_match_expected() {
        let expected = [
            vec![0,1,2,3,4,5],vec![2,1,0,5,4,3],
            vec![5,4,3,2,1,0],vec![3,4,5,0,1,2],
            vec![0,3,1,4,2,5],vec![3,0,4,1,5,2],
            vec![5,2,4,1,3,0],vec![2,5,1,4,0,3],
        ];
        for orientation in 1..=8 {
            for mirror in [false,true] {
                let p=Plan{width:3,height:2,orientation,mirror};
                let (w,h)=p.dimensions();
                let mut actual=Vec::new();
                for y in 0..h { for x in 0..w {
                    let (sx,sy)=p.source(x,y);
                    actual.push(sy*3+sx);
                }}
                let mut wanted=expected[(orientation-1) as usize].clone();
                if mirror { for row in wanted.chunks_mut(w) { row.reverse(); } }
                assert_eq!(actual,wanted);
            }
        }
    }
    #[test]
    fn all_crop_coordinates_stay_in_bounds() {
        for width in 1..=12 { for height in 1..=12 {
            for orientation in 1..=8 { for mirror in [false,true] {
                let p=Plan{width,height,orientation,mirror};
                let (w,h)=p.dimensions();
                for y in 0..h { for x in 0..w {
                    let (sx,sy)=p.source(x,y);
                    assert!(sx<width && sy<height);
                }}
            }}
        }}
    }
}
