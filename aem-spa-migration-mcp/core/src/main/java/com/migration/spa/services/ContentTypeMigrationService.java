package com.migration.spa.services;

import org.apache.sling.api.resource.Resource;

/**
 * Service for detecting AEM content types during migration analysis.
 * Provides fine-grained classification of resources to guide migration decisions.
 */
public interface ContentTypeMigrationService {

    /**
     * Returns true if the resource represents a page component (cq:Page or similar).
     */
    boolean isPageComponent(Resource r);

    /**
     * Returns true if the resource is a parsys (paragraph system) component.
     */
    boolean isParsysComponent(Resource r);

    /**
     * Returns true if the resource is a container component (e.g. responsivegrid).
     */
    boolean isContainerComponent(Resource r);

    /**
     * Returns true if the resource is a form component.
     */
    boolean isFormComponent(Resource r);

    /**
     * Returns true if the resource is an Experience Fragment.
     */
    boolean isExperienceFragment(Resource r);

    /**
     * Returns true if the resource is a Content Fragment.
     */
    boolean isContentFragment(Resource r);

    /**
     * Detects and returns a string representing the content type category.
     * Possible values: PAGE, PARSYS, CONTAINER, FORM, EXPERIENCE_FRAGMENT,
     * CONTENT_FRAGMENT, COMPONENT
     *
     * @param r the resource to classify
     * @return content type string
     */
    String detectContentType(Resource r);
}
