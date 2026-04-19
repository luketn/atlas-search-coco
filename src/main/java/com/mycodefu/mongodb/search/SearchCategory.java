package com.mycodefu.mongodb.search;

import com.mycodefu.model.Image;

import java.util.List;
import java.util.function.Function;

public enum SearchCategory {
    ANIMAL("animal", SearchFilters::animal, Image::animal),
    APPLIANCE("appliance", SearchFilters::appliance, Image::appliance),
    ELECTRONIC("electronic", SearchFilters::electronic, Image::electronic),
    FOOD("food", SearchFilters::food, Image::food),
    FURNITURE("furniture", SearchFilters::furniture, Image::furniture),
    INDOOR("indoor", SearchFilters::indoor, Image::indoor),
    KITCHEN("kitchen", SearchFilters::kitchen, Image::kitchen),
    OUTDOOR("outdoor", SearchFilters::outdoor, Image::outdoor),
    SPORTS("sports", SearchFilters::sports, Image::sports),
    VEHICLE("vehicle", SearchFilters::vehicle, Image::vehicle);

    private static final List<SearchCategory> FILTERABLE = List.of(values());

    private final String fieldName;
    private final Function<SearchFilters, List<String>> filterAccessor;
    private final Function<Image, List<String>> imageAccessor;

    SearchCategory(
            String fieldName,
            Function<SearchFilters, List<String>> filterAccessor,
            Function<Image, List<String>> imageAccessor
    ) {
        this.fieldName = fieldName;
        this.filterAccessor = filterAccessor;
        this.imageAccessor = imageAccessor;
    }

    public String fieldName() {
        return fieldName;
    }

    public List<String> filterValues(SearchFilters filters) {
        return filterAccessor.apply(filters);
    }

    public List<String> imageValues(Image image) {
        return imageAccessor.apply(image);
    }

    public static List<SearchCategory> filterable() {
        return FILTERABLE;
    }
}
