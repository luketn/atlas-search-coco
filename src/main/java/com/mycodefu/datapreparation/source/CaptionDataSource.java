package com.mycodefu.datapreparation.source;

/**
 * Records for data format:
 * {
 *   "info": {
 *     "description": "COCO 2017 Dataset",
 *     "url": "http://cocodataset.org",
 *     "version": "1.0",
 *     "year": 2017,
 *     "contributor": "COCO Consortium",
 *     "date_created": "2017/09/01"
 *   },
 *   "licenses": [
 *     {
 *       "url": "http://creativecommons.org/licenses/by-nc-sa/2.0/",
 *       "id": 1,
 *       "name": "Attribution-NonCommercial-ShareAlike License"
 *     }
 *   ],
 *   "images": [
 *     {
 *       "license": 3,
 *       "file_name": "000000391895.jpg",
 *       "coco_url": "http://images.cocodataset.org/train2017/000000391895.jpg",
 *       "height": 360,
 *       "width": 640,
 *       "date_captured": "2013-11-14 11:18:45",
 *       "flickr_url": "http://farm9.staticflickr.com/8186/8119368305_4e622c8349_z.jpg",
 *       "id": 391895
 *     }
 *   ],
 *   "annotations": [
 *     {
 *       "image_id": 203564,
 *       "id": 37,
 *       "caption": "A bicycle replica with a clock as the front wheel."
 *     }
 *   ]
 * }
 */
public record CaptionDataSource(Info info, License[] licenses, Image[] images, Annotation[] annotations) {
    public record Info(String description, String url, String version, int year, String contributor, String date_created) { }

    public record License(String url, int id, String name) { }

    public record Image(int license, String file_name, String coco_url, int height, int width, String date_captured, String flickr_url, int id) { }

    public record Annotation(int image_id, int id, String caption) { }
}
