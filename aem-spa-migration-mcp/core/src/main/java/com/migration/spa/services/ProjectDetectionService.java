package com.migration.spa.services;

import org.apache.sling.api.resource.ResourceResolver;

import java.util.Set;

/**
 * Detects the source project structure (app namespace, component paths)
 * from the live JCR tree so that the SPA migration can run without any
 * manual OSGi configuration per project.
 */
public interface ProjectDetectionService {

    /**
     * Analyses {@code contentRootPath} and {@code appsRootPath} to determine
     * the source project's app namespace and derive the SPA target namespace.
     */
    ProjectProfile detectProject(ResourceResolver rr, String contentRootPath, String appsRootPath);

    /**
     * Walks the given {@code appsPath} and returns all {@code sling:resourceType}
     * strings that correspond to {@code cq:Component} nodes found there.
     * E.g. walking {@code /apps} and finding {@code /apps/wknd/components/text}
     * (a cq:Component) produces the string {@code "wknd/components/text"}.
     */
    Set<String> discoverComponentResourceTypes(ResourceResolver rr, String appsPath);

    /**
     * Derives the SPA target namespace from a source namespace by convention.
     * If the source already ends with {@code -spa} it is returned unchanged;
     * otherwise {@code -spa} is appended.
     */
    String inferTargetNamespace(String sourceNamespace);

    /**
     * Returns {@code true} for known AEM/Sling framework namespaces that must
     * never be treated as a project source namespace.
     */
    boolean isKnownCoreComponentNamespace(String namespace);

    // -------------------------------------------------------------------------
    // Value object returned by detectProject()
    // -------------------------------------------------------------------------

    class ProjectProfile {

        private final String sourceAppNamespace;
        private final String targetAppNamespace;
        private final String appsRootPath;
        private final Set<String> discoveredResourceTypes;
        private final String detectionConfidence;

        public ProjectProfile(String sourceAppNamespace,
                              String targetAppNamespace,
                              String appsRootPath,
                              Set<String> discoveredResourceTypes,
                              String detectionConfidence) {
            this.sourceAppNamespace   = sourceAppNamespace;
            this.targetAppNamespace   = targetAppNamespace;
            this.appsRootPath         = appsRootPath;
            this.discoveredResourceTypes = discoveredResourceTypes;
            this.detectionConfidence  = detectionConfidence;
        }

        public String getSourceAppNamespace()       { return sourceAppNamespace; }
        public String getTargetAppNamespace()       { return targetAppNamespace; }
        public String getAppsRootPath()             { return appsRootPath; }
        public Set<String> getDiscoveredResourceTypes() { return discoveredResourceTypes; }
        public String getDetectionConfidence()      { return detectionConfidence; }

        @Override
        public String toString() {
            return "ProjectProfile{source='" + sourceAppNamespace + "', target='" + targetAppNamespace
                    + "', confidence='" + detectionConfidence + "', components=" + discoveredResourceTypes.size() + "}";
        }
    }
}
