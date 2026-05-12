package com.migration.spa.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration interface for resource type mappings used during SPA migration.
 * Deploy as com.migration.spa.config.ResourceTypeMappings.cfg.json in /apps.
 */
@ObjectClassDefinition(
        name = "SPA Migration - Resource Type Mappings Configuration",
        description = "Global resource type mapping configuration for SPA migration"
)
public @interface ResourceTypeMappings {

    @AttributeDefinition(
            name = "Resource Type Mappings",
            description = "List of source=target resource type mappings. Format: myapp/components/text=core/wcm/components/text/v2/text"
    )
    String[] resourceTypeMappings() default {
            "myapp/components/page=myapp-spa/components/page",
            "myapp/components/header=myapp-spa/components/header",
            "myapp/components/footer=myapp-spa/components/footer",
            "myapp/components/hero=myapp-spa/components/hero",
            "myapp/components/card=myapp-spa/components/card",
            "myapp/components/cardlist=myapp-spa/components/cardlist",
            "myapp/components/richtext=core/wcm/components/text/v2/text",
            "myapp/components/adaptiveimage=core/wcm/components/image/v3/image"
    };
}
