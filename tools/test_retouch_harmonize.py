import unittest

import numpy as np

from retouch_harmonize_trial import harmonize


class HarmonizeSafeguardsTest(unittest.TestCase):
    def test_sources_masks_and_protected_holes_stay_unchanged(self):
        random=np.random.default_rng(42)
        source=random.integers(90,160,(120,120,3),dtype=np.uint8)
        generated=source.copy()
        generated[20:100,20:100]=[150,130,120]
        mask=np.zeros((120,120),np.uint8)
        mask[20:100,20:100]=255
        mask[48:70,43:78]=0
        old_source=source.copy(); old_generated=generated.copy(); old_mask=mask.copy()
        outputs=harmonize(source,generated,mask,25.)
        np.testing.assert_array_equal(source,old_source)
        np.testing.assert_array_equal(generated,old_generated)
        np.testing.assert_array_equal(mask,old_mask)
        self.assertEqual(len(outputs),5)
        for name,value in outputs.items():
            with self.subTest(name=name):
                self.assertEqual(value.shape,source.shape)
                self.assertEqual(value.dtype,np.uint8)
                np.testing.assert_array_equal(value[mask==0],source[mask==0])


if __name__=="__main__":
    unittest.main()
