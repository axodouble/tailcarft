# Agent instructions

## License headers (required)

This repository is a fork of a BSD-3-Clause project. All code here is owned by
**Jasper (Axodouble) V. <software@jas.pe>**.

Every source file must begin with this copyright header (exact text):

    Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.

    Use of this source code is governed by a BSD-style license that can be
    found in the LICENSE file.

Per-language formatting:

- **Go** — `//` comment block at the top of the file, before the `package`
  clause (and before any package doc comment):

      // Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
      //
      // Use of this source code is governed by a BSD-style license that can be
      // found in the LICENSE file.

- **Java** — `/* ... */` block comment before the `package` declaration:

      /*
       * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
       *
       * Use of this source code is governed by a BSD-style license that can be
       * found in the LICENSE file.
       */

- **Shell** — `#` comments immediately after the shebang line:

      #!/usr/bin/env bash
      # Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
      #
      # Use of this source code is governed by a BSD-style license that can be
      # found in the LICENSE file.

Rules:

- Add the header to every new source file you create.
- When you modify an existing source file that is missing the header, add it
  as part of that same change.
- Never bulk-add or backfill headers on files you are not otherwise editing;
  the header is added only when a file is created or changed.
- Do not put the email address in file headers; the full attribution
  (with contact) lives in this file and in the LICENSE file only.
- Do not add headers to JSON resources, Gradle build files, or generated or
  vendored files where a comment header does not fit.
