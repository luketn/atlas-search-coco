package com.mycodefu.mongodb;

import com.mycodefu.model.Category;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;


public class CategoryDataAccessTest extends AtlasDataTest {

    @Test
    public void list() {
        List<Category> categories = CategoryDataAccess.getInstance().list();
        assertNotNull(categories);
        assertEquals(10, categories.size());

        assertTrue(categories.stream().anyMatch(category ->
                category.superCategory().equals("animal")
                        && category.name().equals("dog")
        ));
    }
}