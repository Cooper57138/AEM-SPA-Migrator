# AEM SPA Migrator

An ACS Commons **Managed Controlled Process (MCP)** OSGi bundle that automates the migration of any AEM HTL/Sightly project to a SPA (Single Page Application) architecture — including multimodule projects — without requiring manual OSGi configuration.

---

## How It Works

The tool auto-detects your project structure from the live JCR tree and executes a 10-step migration pipeline:

| Step | Action | What it does |
|---|---|---|
| 1 | Detect Source Project | BFS-walks `/apps` to find all `cq:Component` nodes, identifies the dominant project namespace (e.g. `wknd`), and derives the SPA target namespace (`wknd-spa`) |
| 2 | Compute Dynamic Mappings | Builds `source → target` resource type mappings from discovered components; merges with any static OSGi config |
| 3 | Audit Content Tree | Traverses the content path, cataloguing every page and component resource type |
| 4 | Generate SPA Component Nodes | Creates `cq:Component` proxy nodes under `/apps/{target-namespace}` with `sling:resourceSuperType` pointing to the source component |
| 5 | Migrate Component Resource Types | Rewrites `sling:resourceType` in all content nodes |
| 6 | Migrate Template Structure Nodes | Rewrites `sling:resourceType` in `/conf` editable template structure nodes |
| 7 | Migrate Template Policy Nodes | Rewrites `sling:resourceType` in `/conf` policy nodes |
| 8 | Scan Sling Models | Reports models missing `@Exporter` (required for the `model.json` SPA endpoint) |
| 9 | Generate SPA Component Stubs | Optionally creates stub nodes for resource types that have no mapping |
| 10 | Produce Migration Report | Persists a full report to `/var/spa-migration/reports/{timestamp}` |

---

## Prerequisites

| Requirement | Version |
|---|---|
| AEM | 6.5+ |
| ACS AEM Commons | 6.2+ |
| Java | 11 |
| Maven | 3.8+ |

ACS AEM Commons must be installed on your AEM instance before deploying this bundle.

---

## Build & Deploy

### Build only

```bash
mvn clean install
```

### Build and deploy to local AEM (localhost:4502)

```bash
mvn clean install -PautoInstallPackage
```

To target a different host or port:

```bash
mvn clean install -PautoInstallPackage \
  -Daem.host=my-aem-host \
  -Daem.port=4503 \
  -Dsling.user=admin \
  -Dsling.password=admin
```

---

## Running the Migration

1. Log in to AEM and navigate to:
   **Tools → ACS Commons → Manage Controlled Processes**

2. Click **Start Process** and select **SPA Migration Process**.

3. Fill in the form fields:

   | Field | Default | Description |
   |---|---|---|
   | Content Root Path | `/content` | Pages and components to scan and migrate |
   | Apps Root Path | `/apps` | Component definitions to scan. Use `/apps/{module}` (e.g. `/apps/wknd`) to target a single module in a multimodule project |
   | Conf Root Path | `/conf` | Editable templates and policies to migrate. Use `/conf/{site}` (e.g. `/conf/wknd`) to scope to a single site |
   | Source App Namespace | _(auto)_ | Leave blank to auto-detect from `/apps`; or supply manually (e.g. `wknd`) |
   | Target App Namespace | _(auto)_ | Leave blank to auto-derive as `{source}-spa`; or supply manually (e.g. `wknd-spa`) |
   | Dry Run | `true` | Log planned changes without writing to JCR |
   | Auto-Generate SPA Components | `true` | Create `cq:Component` proxy nodes under the target namespace |
   | Generate Dynamic Mappings | `true` | Auto-compute mappings from `/apps` (no OSGi config needed) |
   | Migrate Templates | `true` | Rewrite `sling:resourceType` in template structure and policy nodes |
   | Migrate Sling Models | `true` | Report models missing `@Exporter` |
   | Generate SPA Stubs | `false` | Create stub nodes for unmapped resource types |

4. Run with **Dry Run = true** first to preview what will change, then re-run with **Dry Run = false** to apply.

---

## Multimodule Projects

For a project with multiple modules under `/apps` (e.g. `wknd`, `wknd-common`, `wknd-mobile`), scope each run to one module at a time:

| Field | Single-module run | All modules |
|---|---|---|
| Apps Root Path | `/apps/wknd` | `/apps` |
| Conf Root Path | `/conf/wknd` | `/conf` |
| Content Root Path | `/content/wknd` | `/content` |

When `Apps Root Path` points directly at a module (e.g. `/apps/wknd`), the namespace is auto-detected from the components found there and the target namespace is derived automatically (`wknd-spa`).

---

## Migration Report

After each run, a report node is written to:

```
/var/spa-migration/reports/{timestamp}
```

It contains:

| Property | Description |
|---|---|
| `report:status` | `DRY_RUN` or `COMPLETE` |
| `sourceAppNamespace` | Detected or supplied source namespace |
| `targetAppNamespace` | Derived or supplied target namespace |
| `totalPages` | Pages scanned |
| `totalComponents` | Component nodes scanned |
| `migratedComponents` | `sling:resourceType` rewrites in content |
| `generatedComponents` | `cq:Component` nodes created |
| `rewrittenTemplateNodes` | Template structure nodes rewritten |
| `rewrittenPolicyNodes` | Policy nodes rewritten |
| `totalMappings` | Total active mappings used |
| `detectionConfidence` | `HIGH`, `MEDIUM`, or `LOW` |
| `appliedMappings/` | Child nodes listing every `source → target` mapping |
| `modelsNeedingExporter/` | Sling Models that need `@Exporter` |

Browse the report in CRXDE Lite or read it via the JCR API.

---

## Custom Resource Type Mappings

The tool auto-computes mappings at runtime. You can also supply static overrides via OSGi config — static mappings always win over dynamic ones.

Edit the config file at:

```
ui.apps/src/main/content/jcr_root/apps/spa-migration/config/
  com.migration.spa.services.impl.ResourceTypeMappingServiceImpl.cfg.json
```

```json
{
  "resourceTypeMappings": [
    "myapp/components/hero=myapp-spa/components/hero",
    "myapp/components/card=myapp-spa/components/card"
  ]
}
```

### Built-in Default Mappings (Foundation → Core Components)

| Source | Target |
|---|---|
| `foundation/components/text` | `core/wcm/components/text/v2/text` |
| `foundation/components/image` | `core/wcm/components/image/v3/image` |
| `foundation/components/title` | `core/wcm/components/title/v3/title` |
| `foundation/components/list` | `core/wcm/components/list/v3/list` |
| `foundation/components/breadcrumb` | `core/wcm/components/breadcrumb/v3/breadcrumb` |
| `foundation/components/navigation` | `core/wcm/components/navigation/v1/navigation` |
| `foundation/components/carousel` | `core/wcm/components/carousel/v1/carousel` |
| `foundation/components/tabs` | `core/wcm/components/tabs/v1/tabs` |
| `foundation/components/accordion` | `core/wcm/components/accordion/v1/accordion` |
| `wcm/foundation/components/parsys` | `wcm/foundation/components/responsivegrid` |

---

## Post-Migration Checklist

After running with **Dry Run = false**:

- [ ] Review the report at `/var/spa-migration/reports/{timestamp}`
- [ ] Verify migrated pages render in AEM editor and preview
- [ ] Fix Sling Models listed in `modelsNeedingExporter` — add `@Exporter(name="jackson", extensions="json")` and `@ExporterOptions(selectors="model")`
- [ ] Confirm editable templates have correct `allowedComponents` policies for new resource types
- [ ] Update HTL templates where SPA-compatible markup is needed
- [ ] Validate Content Fragments and Experience Fragments are accessible via the JSON API
- [ ] Run regression tests across all migrated page templates

---

## Module Structure

```
aem-spa-migration-mcp/
├── pom.xml                                  # Parent POM
├── core/                                    # OSGi bundle (Java)
│   └── src/main/java/com/migration/spa/
│       ├── mcp/
│       │   ├── SPAMigrationProcess.java      # 10-step MCP process
│       │   └── SPAMigrationProcessFactory.java
│       └── services/
│           ├── ProjectDetectionService.java  # Namespace auto-detection
│           ├── SPAComponentGeneratorService.java  # cq:Component node creation
│           ├── ComponentMigrationService.java
│           ├── TemplateMigrationService.java
│           ├── ResourceTypeMappingService.java
│           ├── SlingModelScannerService.java
│           └── impl/                        # OSGi service implementations
├── ui.apps/                                 # AEM content package
│   └── src/main/content/jcr_root/apps/
│       ├── spa-migration/config/            # OSGi .cfg.json files
│       ├── myapp-spa/                       # Example SPA component set
│       └── wknd-spa/                        # WKND SPA component set
└── all/                                     # Container package
```
