package com.migration.spa;

import com.migration.spa.services.impl.ContentTypeMigrationServiceImpl;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ContentTypeMigrationServiceImplTest {

    private ContentTypeMigrationServiceImpl service;

    @Before
    public void setUp() {
        service = new ContentTypeMigrationServiceImpl();
    }

    private Resource mockResource(String primaryType, String resourceType, String path) {
        Resource resource = mock(Resource.class);
        Map<String, Object> props = new HashMap<>();
        if (primaryType != null) {
            props.put("jcr:primaryType", primaryType);
        }
        if (resourceType != null) {
            props.put("sling:resourceType", resourceType);
        }
        ValueMap vm = new ValueMapDecorator(props);
        when(resource.getValueMap()).thenReturn(vm);
        if (path != null) {
            when(resource.getPath()).thenReturn(path);
        }
        return resource;
    }

    // --- isPageComponent tests ---

    @Test
    public void testIsPageComponent_cqPagePrimaryType() {
        Resource r = mockResource("cq:Page", null, "/content/mysite");
        assertTrue(service.isPageComponent(r));
    }

    @Test
    public void testIsPageComponent_pageResourceType() {
        Resource r = mockResource("nt:unstructured", "myapp/components/page", "/content/mysite/jcr:content");
        assertTrue(service.isPageComponent(r));
    }

    @Test
    public void testIsPageComponent_notPage() {
        Resource r = mockResource("nt:unstructured", "foundation/components/text", "/content/mysite/jcr:content/par/text");
        assertFalse(service.isPageComponent(r));
    }

    @Test
    public void testIsPageComponent_null() {
        assertFalse(service.isPageComponent(null));
    }

    // --- isParsysComponent tests ---

    @Test
    public void testIsParsysComponent_foundationParsys() {
        Resource r = mockResource("nt:unstructured", "foundation/components/parsys", "/content/par");
        assertTrue(service.isParsysComponent(r));
    }

    @Test
    public void testIsParsysComponent_wcmParsys() {
        Resource r = mockResource("nt:unstructured", "wcm/foundation/components/parsys", "/content/par");
        assertTrue(service.isParsysComponent(r));
    }

    @Test
    public void testIsParsysComponent_notParsys() {
        Resource r = mockResource("nt:unstructured", "foundation/components/text", "/content/par/text");
        assertFalse(service.isParsysComponent(r));
    }

    @Test
    public void testIsParsysComponent_null() {
        assertFalse(service.isParsysComponent(null));
    }

    // --- isContainerComponent tests ---

    @Test
    public void testIsContainerComponent_responsivegrid() {
        Resource r = mockResource("nt:unstructured", "wcm/foundation/components/responsivegrid", "/content/page/jcr:content/root");
        assertTrue(service.isContainerComponent(r));
    }

    @Test
    public void testIsContainerComponent_container() {
        Resource r = mockResource("nt:unstructured", "core/wcm/components/container/v1/container", "/content/page/jcr:content/root");
        assertTrue(service.isContainerComponent(r));
    }

    @Test
    public void testIsContainerComponent_notContainer() {
        Resource r = mockResource("nt:unstructured", "foundation/components/text", "/content/par/text");
        assertFalse(service.isContainerComponent(r));
    }

    // --- isFormComponent tests ---

    @Test
    public void testIsFormComponent_formBegin() {
        Resource r = mockResource("nt:unstructured", "foundation/components/form/start", "/content/form");
        assertTrue(service.isFormComponent(r));
    }

    @Test
    public void testIsFormComponent_notForm() {
        Resource r = mockResource("nt:unstructured", "foundation/components/text", "/content/text");
        assertFalse(service.isFormComponent(r));
    }

    @Test
    public void testIsFormComponent_null() {
        assertFalse(service.isFormComponent(null));
    }

    // --- isExperienceFragment tests ---

    @Test
    public void testIsExperienceFragment_byPath() {
        Resource r = mockResource("cq:Page", null, "/content/experience-fragments/mysite/myxf");
        assertTrue(service.isExperienceFragment(r));
    }

    @Test
    public void testIsExperienceFragment_byResourceType() {
        Resource r = mockResource("nt:unstructured", "core/wcm/components/experience-fragment/v1/experience-fragment", "/content/page");
        // Path does not start with /content/experience-fragments but resourceType matches
        assertTrue(service.isExperienceFragment(r));
    }

    @Test
    public void testIsExperienceFragment_notXF() {
        Resource r = mockResource("cq:Page", "myapp/components/page", "/content/mysite/page");
        assertFalse(service.isExperienceFragment(r));
    }

    // --- isContentFragment tests ---

    @Test
    public void testIsContentFragment_byResourceType() {
        Resource r = mockResource("nt:unstructured", "dam/gui/components/admin/asset/content-fragment", "/content/dam/myxf");
        assertTrue(service.isContentFragment(r));
    }

    @Test
    public void testIsContentFragment_notCF() {
        Resource r = mockResource("nt:unstructured", "foundation/components/text", "/content/text");
        assertFalse(service.isContentFragment(r));
    }

    @Test
    public void testIsContentFragment_null() {
        assertFalse(service.isContentFragment(null));
    }

    // --- detectContentType tests ---

    @Test
    public void testDetectContentType_page() {
        Resource r = mockResource("cq:Page", null, "/content/mysite");
        assertEquals("PAGE", service.detectContentType(r));
    }

    @Test
    public void testDetectContentType_parsys() {
        Resource r = mockResource("nt:unstructured", "foundation/components/parsys", "/content/par");
        assertEquals("PARSYS", service.detectContentType(r));
    }

    @Test
    public void testDetectContentType_container() {
        Resource r = mockResource("nt:unstructured", "wcm/foundation/components/responsivegrid", "/content/root");
        assertEquals("CONTAINER", service.detectContentType(r));
    }

    @Test
    public void testDetectContentType_form() {
        Resource r = mockResource("nt:unstructured", "foundation/components/form/start", "/content/form");
        assertEquals("FORM", service.detectContentType(r));
    }

    @Test
    public void testDetectContentType_xf() {
        Resource r = mockResource("cq:Page", "wcm/foundation/components/page", "/content/experience-fragments/myxf");
        assertEquals("EXPERIENCE_FRAGMENT", service.detectContentType(r));
    }

    @Test
    public void testDetectContentType_component() {
        Resource r = mockResource("nt:unstructured", "foundation/components/text", "/content/text");
        assertEquals("COMPONENT", service.detectContentType(r));
    }

    @Test
    public void testDetectContentType_null_returnsComponent() {
        assertEquals("COMPONENT", service.detectContentType(null));
    }
}
