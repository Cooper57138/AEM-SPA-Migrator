package com.migration.spa;

import com.migration.spa.services.ResourceTypeMappingService;
import com.migration.spa.services.impl.ComponentMigrationServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ComponentMigrationServiceImplTest {

    @Mock
    private ResourceTypeMappingService resourceTypeMappingService;

    @InjectMocks
    private ComponentMigrationServiceImpl service;

    @Before
    public void setUp() {
        when(resourceTypeMappingService.getMappings()).thenReturn(Collections.emptyMap());
        service.activate();
    }

    // --- Default mapping tests ---

    @Test
    public void testDefaultMapping_text() {
        String result = service.resolveTargetResourceType("foundation/components/text");
        assertEquals("core/wcm/components/text/v2/text", result);
    }

    @Test
    public void testDefaultMapping_image() {
        String result = service.resolveTargetResourceType("foundation/components/image");
        assertEquals("core/wcm/components/image/v3/image", result);
    }

    @Test
    public void testDefaultMapping_title() {
        String result = service.resolveTargetResourceType("foundation/components/title");
        assertEquals("core/wcm/components/title/v3/title", result);
    }

    @Test
    public void testDefaultMapping_list() {
        String result = service.resolveTargetResourceType("foundation/components/list");
        assertEquals("core/wcm/components/list/v3/list", result);
    }

    @Test
    public void testDefaultMapping_breadcrumb() {
        String result = service.resolveTargetResourceType("foundation/components/breadcrumb");
        assertEquals("core/wcm/components/breadcrumb/v3/breadcrumb", result);
    }

    @Test
    public void testDefaultMapping_navigation() {
        String result = service.resolveTargetResourceType("foundation/components/navigation");
        assertEquals("core/wcm/components/navigation/v1/navigation", result);
    }

    @Test
    public void testDefaultMapping_carousel() {
        String result = service.resolveTargetResourceType("foundation/components/carousel");
        assertEquals("core/wcm/components/carousel/v1/carousel", result);
    }

    @Test
    public void testDefaultMapping_tabs() {
        String result = service.resolveTargetResourceType("foundation/components/tabs");
        assertEquals("core/wcm/components/tabs/v1/tabs", result);
    }

    @Test
    public void testDefaultMapping_accordion() {
        String result = service.resolveTargetResourceType("foundation/components/accordion");
        assertEquals("core/wcm/components/accordion/v1/accordion", result);
    }

    @Test
    public void testDefaultMapping_parsys() {
        String result = service.resolveTargetResourceType("wcm/foundation/components/parsys");
        assertEquals("wcm/foundation/components/responsivegrid", result);
    }

    // --- Custom mapping override tests ---

    @Test
    public void testCustomMappingOverridesDefault() {
        Map<String, String> customMappings = new HashMap<>();
        customMappings.put("foundation/components/text", "myapp/components/custom-text");
        when(resourceTypeMappingService.getMappings()).thenReturn(customMappings);
        service.activate();

        String result = service.resolveTargetResourceType("foundation/components/text");
        assertEquals("myapp/components/custom-text", result);
    }

    @Test
    public void testCustomMappingAddsNewEntry() {
        Map<String, String> customMappings = new HashMap<>();
        customMappings.put("myapp/components/hero", "myapp-spa/components/hero");
        when(resourceTypeMappingService.getMappings()).thenReturn(customMappings);
        service.activate();

        String result = service.resolveTargetResourceType("myapp/components/hero");
        assertEquals("myapp-spa/components/hero", result);
    }

    // --- hasMappingFor tests ---

    @Test
    public void testHasMappingFor_knownType() {
        assertTrue(service.hasMappingFor("foundation/components/text"));
    }

    @Test
    public void testHasMappingFor_unknownType() {
        assertFalse(service.hasMappingFor("some/completely/unknown/component"));
    }

    @Test
    public void testHasMappingFor_nullType() {
        assertFalse(service.hasMappingFor(null));
    }

    @Test
    public void testHasMappingFor_emptyType() {
        assertFalse(service.hasMappingFor(""));
    }

    // --- resolveTargetResourceType edge cases ---

    @Test
    public void testResolveNull_returnsNull() {
        assertNull(service.resolveTargetResourceType(null));
    }

    @Test
    public void testResolveEmpty_returnsNull() {
        assertNull(service.resolveTargetResourceType(""));
    }

    @Test
    public void testResolveUnknown_returnsNull() {
        assertNull(service.resolveTargetResourceType("unknown/type"));
    }

    // --- getAllMappings tests ---

    @Test
    public void testGetAllMappings_containsDefaults() {
        Map<String, String> all = service.getAllMappings();
        assertTrue(all.containsKey("foundation/components/text"));
        assertTrue(all.containsKey("foundation/components/image"));
        assertEquals(10, all.size());
    }

    @Test
    public void testGetAllMappings_mergesCustom() {
        Map<String, String> customMappings = new HashMap<>();
        customMappings.put("myapp/components/custom", "myapp-spa/components/custom");
        when(resourceTypeMappingService.getMappings()).thenReturn(customMappings);
        service.activate();

        Map<String, String> all = service.getAllMappings();
        assertTrue(all.containsKey("myapp/components/custom"));
        assertTrue(all.containsKey("foundation/components/text"));
        assertEquals(11, all.size());
    }
}
