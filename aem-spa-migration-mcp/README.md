# AEM SPA Migration MCP

An ACS Commons **Managed Controlled Process (MCP)** OSGi bundle that automates the migration of a multimodule AEM Sightly/HTL project to a SPA (Single Page Application) architecture.

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

## Running the MCP Process

1. Log in to AEM and navigate to:
   **Tools → ACS Commons → Manage Controlled Processes**

2. Click **"Start Process"** and select **"SPA Migration Process"** from the list.

3. Fill in the form fields:

   | Field | Default | Description |
   |---|---|---|
   | Content Root Path | `/content` | JCR path to scan for pages and components |
   | Apps Root Path | `/apps` | Path for template and Sling Model scanning |
   | Dry Run | `true` | Report only — no JCR writes |
   | Migrate Templates | `true` | Enable SPA flag on editable templates |
   | Migrate Dialogs | `true` | Process Touch UI dialogs |
   | Migrate Sling Models | `true` | Flag models missing `@Exporter` |
   | Generate SPA Stubs | `false` | Create React stub JSON descriptors |
   | Resource Type Mapping Config | _(empty)_ | Custom OSGi config path (optional) |

4. Click **"Start"** to begin. The MCP dashboard shows real-time progress for each of the six pipeline steps:
   - **Audit Content Tree** — traverses pages and components
   - **Migrate Component Resource Types** — rewrites `sling:resourceType` values
   - **Migrate Editable Templates** — sets `spa:enabled = true`
   - **Scan Sling Models** — detects models missing `@Exporter`
   - **Generate SPA Component Stubs** — writes stub JSON descriptor nodes
   - **Produce Migration Report** — persists the final report to JCR

5. Download the report from:
   ```
   /var/spa-migration/reports/{timestamp}
   ```
   Or browse it in CRXDE Lite / the JCR explorer.

---

## Configuration

### Custom Resource Type Mappings

Edit the OSGi configuration file at:
```
ui.apps/src/main/content/jcr_root/apps/spa-migration/config/
  com.migration.spa.services.impl.ResourceTypeMappingServiceImpl.cfg.json
```

Add entries in `source=target` format:

```json
{
  "resourceTypeMappings": [
    "myapp/components/hero=myapp-spa/components/hero",
    "myapp/components/card=myapp-spa/components/card",
    "myapp/components/richtext=core/wcm/components/text/v2/text"
  ]
}
```

Custom mappings **override** the built-in default Foundation → Core Component mappings.

### Built-in Default Mappings

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

## Extending the Bundle

### Adding New Content Type Detectors

1. Create a new implementation of `ContentTypeMigrationService`:

```java
@Component(service = ContentTypeMigrationService.class)
public class MyCustomContentTypeMigrationServiceImpl implements ContentTypeMigrationService {
    // Override detection logic
}
```

2. Reference it from `SPAMigrationProcess` or use it standalone in a custom MCP step.

### Adding New Resource Type Mappings at Runtime

You can deploy additional `.cfg.json` files targeting `ResourceTypeMappingServiceImpl`
in different run-mode config folders (e.g. `config.author`) to apply environment-specific mappings.

---

## Post-Migration Checklist

After running the process with `dryRun = false`:

- [ ] Review `/var/spa-migration/reports/{timestamp}` for migrated component count and any errors
- [ ] Verify migrated pages render correctly in the AEM editor and preview
- [ ] Fix Sling Models listed in `modelsNeedingExporter` by adding `@Exporter(name = "jackson", extensions = "json")` and `@ExporterOptions(selectors = "model")`
- [ ] Update HTL/Sightly templates to use SPA-compatible markup where required
- [ ] Verify editable templates have correct `allowedComponents` policies for new resource types
- [ ] Run regression tests on all migrated page templates
- [ ] Remove or update custom dialog definitions that no longer match new component structures
- [ ] Validate Content Fragments and Experience Fragments are accessible via the SPA JSON API
- [ ] Configure the AEM SPA Editor (if using the WYSIWYG SPA editor approach) with correct routing
- [ ] Re-run with `dryRun = true` on production-like content to estimate scope before going live

---

## Module Structure

```
aem-spa-migration-mcp/
├── pom.xml                              # Parent POM
├── core/                                # OSGi bundle (Java)
│   └── src/main/java/com/migration/spa/
│       ├── mcp/                         # MCP process classes
│       ├── services/                    # Service interfaces + impls
│       └── config/                      # OSGi config interfaces
├── ui.apps/                             # AEM content package
│   └── src/main/content/jcr_root/
│       └── apps/spa-migration/
│           ├── config/                  # OSGi .cfg.json files
│           └── components/             # Component placeholders
└── all/                                 # Container package
```
