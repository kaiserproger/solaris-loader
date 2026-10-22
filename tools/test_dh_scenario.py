import tempfile
import unittest
from pathlib import Path

from PIL import Image, ImageDraw
from dh_scenario import landmark_pixels


class LandmarkPixelTests(unittest.TestCase):
    def image(self, directory, color=None):
        path = Path(directory) / "landmark.png"
        image = Image.new("RGB", (854, 480), (175, 203, 255))
        draw = ImageDraw.Draw(image)
        if color is not None:
            draw.rectangle((427, 218, 429, 251), fill=color)
        # Nearby greenery below the pillar must not satisfy the edited marker.
        draw.rectangle((425, 244, 432, 260), fill=(50, 160, 100))
        image.save(path)
        return path

    def test_gold_pixels_cannot_pass_an_emerald_edit(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.image(directory, (137, 123, 62))
            self.assertTrue(landmark_pixels(path, "gold")["passed"])
            self.assertFalse(landmark_pixels(path, "emerald")["passed"])

    def test_sky_and_nearby_greenery_are_not_a_distant_marker(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.image(directory)
            self.assertFalse(landmark_pixels(path, "gold")["passed"])
            self.assertFalse(landmark_pixels(path, "emerald")["passed"])

    def test_real_marker_region_accepts_green_and_retains_bounds(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.image(directory, (50, 160, 100))
            observed = landmark_pixels(path, "emerald")
            self.assertTrue(observed["passed"])
            self.assertEqual(observed["region"], [425, 220, 432, 241])
            self.assertFalse(landmark_pixels(path, "gold")["passed"])


if __name__ == "__main__":
    unittest.main()
