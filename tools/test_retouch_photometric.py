import unittest
import numpy as np
from retouch_photometric import camera_match, inward_compose, screened_compose


class CameraMatchingTest(unittest.TestCase):
    def test_camera_bias_is_reduced_without_modifying_inputs(self):
        y,x=np.mgrid[:100,:100]
        source=np.stack([110+x*.2,100+y*.15,95+x*.1],axis=-1).astype(np.uint8)
        candidate=np.clip(source.astype(np.int16)+[12,-3,4],0,255).astype(np.uint8)
        reference=np.full((100,100),255,np.uint8); reference[35:70,30:75]=0
        old_source=source.copy(); old_candidate=candidate.copy()
        result=camera_match(source,candidate,reference)
        old_error=np.abs(source.astype(float)-candidate).mean()
        self.assertLess(np.abs(source.astype(float)-result).mean(),old_error*.4)
        np.testing.assert_array_equal(source,old_source)
        np.testing.assert_array_equal(candidate,old_candidate)

    def test_feathering_never_changes_protected_holes_or_outside(self):
        source=np.full((100,100,3),90,np.uint8)
        candidate=np.full_like(source,150)
        mask=np.zeros((100,100),np.uint8); mask[10:90,10:90]=255; mask[40:55,40:55]=0
        old_mask=mask.copy()
        result=inward_compose(source,candidate,mask,30)
        np.testing.assert_array_equal(result[mask==0],source[mask==0])
        np.testing.assert_array_equal(mask,old_mask)
        self.assertEqual(result[25,25,0],150)

    def test_insufficient_skin_reference_is_not_silently_accepted(self):
        source=np.full((20,20,3),100,np.uint8)
        with self.assertRaises(ValueError): camera_match(source,source,np.zeros((20,20),np.uint8))

    def test_screened_preserves_holes_without_reintroducing_dark_interior(self):
        source=np.full((100,100,3),70,np.uint8)
        candidate=np.full_like(source,140)
        mask=np.zeros((100,100),np.uint8); mask[5:95,5:95]=255; mask[45:55,45:55]=0
        old_mask=mask.copy(); old_source=source.copy(); old_candidate=candidate.copy()
        result=screened_compose(source,candidate,mask)
        np.testing.assert_array_equal(result[mask==0],source[mask==0])
        np.testing.assert_array_equal(source,old_source)
        np.testing.assert_array_equal(candidate,old_candidate)
        np.testing.assert_array_equal(mask,old_mask)
        self.assertGreater(result[25,25,0],135)

    def test_screened_identity_and_empty_mask(self):
        source=np.random.default_rng(3).integers(0,256,(30,30,3),dtype=np.uint8)
        mask=np.full((30,30),255,np.uint8)
        np.testing.assert_array_equal(screened_compose(source,source,mask),source)
        np.testing.assert_array_equal(screened_compose(source,255-source,mask*0),source)


if __name__=="__main__": unittest.main()
