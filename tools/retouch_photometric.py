"""Bounded camera-color matching on clean reference skin; does not copy beard pixels."""
import cv2
import numpy as np


def camera_match(source, generated, reference):
    if source.shape!=generated.shape or reference.shape!=source.shape[:2]:
        raise ValueError("Mismatched image geometry")
    before=cv2.cvtColor(source.astype(np.float32)/255,cv2.COLOR_RGB2Lab)
    after=cv2.cvtColor(generated.astype(np.float32)/255,cv2.COLOR_RGB2Lab)
    clean=(reference>0)&(before[:,:,0]>10)&(before[:,:,0]<95)
    y,x=np.nonzero(clean)
    if len(x)<64:
        raise ValueError("Not enough clean skin for camera matching")
    step=max(1,len(x)//6000); x=x[::step]; y=y[::step]
    h,w=reference.shape
    xx=x.astype(np.float32)/max(1,w-1)*2-1
    yy=y.astype(np.float32)/max(1,h-1)*2-1
    design=np.column_stack([np.ones(len(x)),xx,yy,xx*yy])
    target=(cv2.GaussianBlur(before,(0,0),1.)-cv2.GaussianBlur(after,(0,0),1.))[y,x]
    grid_y,grid_x=np.mgrid[:h,:w].astype(np.float32)
    grid_x=grid_x/max(1,w-1)*2-1; grid_y=grid_y/max(1,h-1)*2-1
    basis=np.stack([np.ones_like(grid_x),grid_x,grid_y,grid_x*grid_y],axis=-1)
    correction=np.zeros_like(after)
    for channel,limit in enumerate([8.,5.,5.]):
        values=target[:,channel]
        weights=np.ones(len(x),np.float32)
        coefficients=np.zeros(4)
        for _ in range(5):
            regularizer=np.diag([.001,.08,.08,.2])
            coefficients=np.linalg.solve(design.T@(design*weights[:,None])+regularizer,design.T@(values*weights))
            residual=values-design@coefficients
            scale=max(.2,float(np.median(np.abs(residual-np.median(residual))))*1.4826)
            weights=np.minimum(1.,2.*scale/np.maximum(np.abs(residual),1e-6))
        correction[:,:,channel]=np.clip(basis@coefficients,-limit,limit)
    adjusted=after+correction
    adjusted[:,:,0]=np.clip(adjusted[:,:,0],0,100)
    return np.rint(cv2.cvtColor(adjusted,cv2.COLOR_Lab2RGB)*255).clip(0,255).astype(np.uint8)


def inward_compose(source, generated, mask, span):
    distance=cv2.distanceTransform(mask,cv2.DIST_L2,5)
    t=np.clip(distance/max(2.,span*.12),0,1)
    alpha=(t*t*(3-2*t))[:,:,None]
    output=np.rint(source*(1-alpha)+generated*alpha).clip(0,255).astype(np.uint8)
    output[mask==0]=source[mask==0]
    return output


def screened_compose(source, generated, mask, strength=.08):
    """Keep generated shading inside the edit while matching original boundary pixels."""
    from scipy.sparse import coo_matrix
    from scipy.sparse.linalg import spsolve

    if source.shape != generated.shape or mask.shape != source.shape[:2]:
        raise ValueError("Mismatched image geometry")
    if strength <= 0:
        raise ValueError("Screening must be positive")
    active = mask > 0
    active = active.copy()
    active[[0, -1], :] = False
    active[:, [0, -1]] = False
    y, x = np.nonzero(active)
    if not len(x):
        return source.copy()
    index = np.full(mask.shape, -1, np.int32)
    index[y, x] = np.arange(len(x))
    rows = [np.arange(len(x))]
    cols = [np.arange(len(x))]
    values = [np.full(len(x), 4 + strength)]
    edited = generated.astype(np.float64)
    original = source.astype(np.float64)
    rhs = (4 + strength) * edited[y, x]
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        ny, nx = y + dy, x + dx
        adjacent = index[ny, nx]
        inside = adjacent >= 0
        rows.append(np.flatnonzero(inside))
        cols.append(adjacent[inside])
        values.append(np.full(inside.sum(), -1.))
        rhs -= edited[ny, nx]
        rhs[~inside] += original[ny[~inside], nx[~inside]]
    system = coo_matrix((np.concatenate(values), (np.concatenate(rows), np.concatenate(cols))),
                        shape=(len(x), len(x))).tocsc()
    output = source.copy()
    output[y, x] = np.rint(spsolve(system, rhs)).clip(0, 255).astype(np.uint8)
    return output
