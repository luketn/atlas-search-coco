package com.mycodefu.datapreparation.source;

/**
 * Data source: {
 *   "annotations": [
 *     {
 *       "segmentation": [
 *         [
 *           239.97,
 *           260.24,
 *           222.04,
 *           270.49,
 *           199.84,
 *           253.41,
 *           213.5
 *         ]
 *       ],
 *       "area": 2765.1486500000005,
 *       "iscrowd": 0,
 *       "image_id": 558840,
 *       "bbox": [
 *         199.84,
 *         200.46,
 *         77.71,
 *         70.88
 *       ],
 *       "category_id": 58,
 *       "id": 156
 *     }
 *   ],
 *   "categories": [
 *     {
 *       "supercategory": "person",
 *       "id": 1,
 *       "name": "person"
 *     },
 *     {
 *       "supercategory": "vehicle",
 *       "id": 2,
 *       "name": "bicycle"
 *     },
 *     {
 *       "supercategory": "vehicle",
 *       "id": 3,
 *       "name": "car"
 *     }
 *   ]
 * }
 */
public record InstanceDataSource(Annotation[] annotations, Category[] categories) {
    public record Annotation(
//            double[][] segmentation,
//            double area,
//            int iscrowd,
            int image_id,
            double[] bbox,
            int category_id
//            int id
    ) {}
    public record Category(
            int id,
            String supercategory,
            String name
    ) {}
}
