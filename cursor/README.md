# Automatic Project Extensions

Open projects normally in Cursor. Local workspaces receive their own extension selection and state history. A changed selection can reload that window once. Built-ins, application-scoped extensions, remote access and manual choices remain respected.

The implementation validates Cursor's internal workspace storage scope and verifies writes, restoring the prior list on mismatch. Unsupported API shapes stop modification. Incomplete scans retain extensions conservatively.

Based on Auto Disable Extensions 0.2.2 by ElPandaP (MIT). See LICENSE.upstream and UPSTREAM.json. This fork preserves the existing extension identifier for upgrade continuity.
