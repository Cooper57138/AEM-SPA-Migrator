package com.migration.spa.services.impl;

import com.migration.spa.services.ProjectDetectionService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component(service = ProjectDetectionService.class, immediate = true)
public class ProjectDetectionServiceImpl implements ProjectDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ProjectDetectionServiceImpl.class);

    private static final String PROP_PRIMARY_TYPE = "jcr:primaryType";
    private static final String TYPE_CQ_COMPONENT = "cq:Component";
    private static final String TYPE_SLING_FOLDER = "sling:Folder";
    private static final String TYPE_NT_FOLDER = "nt:folder";
    private static final String TYPE_SLING_ORDERED_FOLDER = "sling:OrderedFolder";

    private static final Set<String> CORE_NAMESPACES;

    static {
        Set<String> coreNs = new HashSet<>();
        coreNs.add("core");
        coreNs.add("wcm");
        coreNs.add("foundation");
        coreNs.add("granite");
        coreNs.add("cq");
        coreNs.add("dam");
        coreNs.add("social");
        coreNs.add("communities");
        coreNs.add("commerce");
        coreNs.add("acs-commons");
        coreNs.add("acs");
        CORE_NAMESPACES = Collections.unmodifiableSet(coreNs);
    }

    @Override
    public ProjectProfile detectProject(ResourceResolver rr, String contentRootPath, String appsRootPath) {
        log.info("Detecting project structure. appsRoot={}, contentRoot={}", appsRootPath, contentRootPath);

        // 1. Discover all cq:Component resource types under /apps
        Set<String> allDiscovered = discoverComponentResourceTypes(rr, appsRootPath);
        log.info("Discovered {} cq:Component resource types under {}", allDiscovered.size(), appsRootPath);

        // 2. Build frequency map: first-level namespace -> component count
        Map<String, Integer> namespaceCounts = new HashMap<>();
        for (String rt : allDiscovered) {
            String ns = extractNamespace(rt);
            if (ns != null && !isKnownCoreComponentNamespace(ns)) {
                namespaceCounts.merge(ns, 1, Integer::sum);
            }
        }

        if (namespaceCounts.isEmpty()) {
            log.warn("No project namespaces found under {}. Using fallback detection.", appsRootPath);
            return new ProjectProfile("unknown", "unknown-spa", appsRootPath,
                    allDiscovered, "LOW");
        }

        // 3. Pick the namespace with the most components
        String topNamespace = namespaceCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (topNamespace == null) {
            return new ProjectProfile("unknown", "unknown-spa", appsRootPath,
                    allDiscovered, "LOW");
        }

        // 4. Verify against content tree to assign confidence
        String confidence = "MEDIUM";
        if (contentRootPath != null && !contentRootPath.trim().isEmpty()) {
            int contentRefs = countContentReferences(rr, contentRootPath, topNamespace);
            if (contentRefs > 0) {
                confidence = "HIGH";
                log.info("Confidence HIGH: namespace '{}' referenced {} times in content tree", topNamespace, contentRefs);
            }
        }

        String targetNs = inferTargetNamespace(topNamespace);
        // If the caller already pointed appsRootPath at the specific module (e.g. /apps/wknd),
        // don't append the namespace again.
        String detectedAppsPath = appsRootPath.endsWith("/" + topNamespace)
                ? appsRootPath
                : appsRootPath + "/" + topNamespace;

        log.info("Detected project: source='{}', target='{}', confidence={}, components={}",
                topNamespace, targetNs, confidence, namespaceCounts.get(topNamespace));

        return new ProjectProfile(topNamespace, targetNs, detectedAppsPath, allDiscovered, confidence);
    }

    @Override
    public Set<String> discoverComponentResourceTypes(ResourceResolver rr, String appsPath) {
        Resource appsResource = rr.getResource(appsPath);
        if (appsResource == null) {
            log.warn("Apps path not found: {}", appsPath);
            return Collections.emptySet();
        }

        Set<String> result = new LinkedHashSet<>();
        // BFS using a deque to avoid stack overflow on deep trees
        Deque<Resource> queue = new ArrayDeque<>();
        queue.add(appsResource);

        while (!queue.isEmpty()) {
            Resource current = queue.poll();
            String primaryType = current.getValueMap().get(PROP_PRIMARY_TYPE, String.class);

            if (TYPE_CQ_COMPONENT.equals(primaryType)) {
                // Compute resource type relative to /apps
                String absolutePath = current.getPath();
                if (absolutePath.startsWith("/apps/")) {
                    result.add(absolutePath.substring("/apps/".length()));
                }
                // Do NOT recurse into component children (dialogs, etc.)
                continue;
            }

            // Only recurse into folder-type nodes
            if (isFolderType(primaryType)) {
                Iterator<Resource> children = current.listChildren();
                while (children.hasNext()) {
                    queue.add(children.next());
                }
            }
        }

        return Collections.unmodifiableSet(result);
    }

    @Override
    public String inferTargetNamespace(String sourceNamespace) {
        if (sourceNamespace == null || sourceNamespace.trim().isEmpty()) {
            return "spa";
        }
        if (sourceNamespace.endsWith("-spa")) {
            return sourceNamespace;
        }
        return sourceNamespace + "-spa";
    }

    @Override
    public boolean isKnownCoreComponentNamespace(String namespace) {
        return namespace != null && CORE_NAMESPACES.contains(namespace.toLowerCase());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Extracts the first path segment (namespace) from a resource type string.
     *  "wknd/components/text" -> "wknd"
     *  "myapp/components/page" -> "myapp"
     */
    private String extractNamespace(String resourceType) {
        if (resourceType == null || resourceType.isEmpty()) {
            return null;
        }
        int slash = resourceType.indexOf('/');
        return slash > 0 ? resourceType.substring(0, slash) : resourceType;
    }

    private boolean isFolderType(String primaryType) {
        return TYPE_SLING_FOLDER.equals(primaryType)
                || TYPE_NT_FOLDER.equals(primaryType)
                || TYPE_SLING_ORDERED_FOLDER.equals(primaryType)
                || "nt:unstructured".equals(primaryType);  // some /apps roots use nt:unstructured
    }

    /** Walks the content tree and counts how many jcr:content nodes have
     *  a sling:resourceType starting with the given namespace prefix. */
    private int countContentReferences(ResourceResolver rr, String contentRoot, String namespace) {
        Resource root = rr.getResource(contentRoot);
        if (root == null) {
            return 0;
        }
        String prefix = namespace + "/";
        int[] count = {0};
        walkForResourceTypeCount(root, prefix, count, 0, 8 /* max depth */);
        return count[0];
    }

    private void walkForResourceTypeCount(Resource resource, String prefix, int[] count,
                                          int depth, int maxDepth) {
        if (resource == null || depth > maxDepth) {
            return;
        }
        String rt = resource.getValueMap().get("sling:resourceType", String.class);
        if (rt != null && rt.startsWith(prefix)) {
            count[0]++;
        }
        Iterator<Resource> children = resource.listChildren();
        while (children.hasNext()) {
            walkForResourceTypeCount(children.next(), prefix, count, depth + 1, maxDepth);
        }
    }
}
