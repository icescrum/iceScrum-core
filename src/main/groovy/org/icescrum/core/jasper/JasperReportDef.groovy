/*
 * Copyright (c) 2026 iceScrum community.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * Minimal replacement for the Grails 2 jasper plugin's JasperReportDef.
 */
package org.icescrum.core.jasper

class JasperReportDef {
    String name
    String folder = 'reports'
    Collection reportData
    JasperExportFormat fileFormat = JasperExportFormat.PDF_FORMAT
    Locale locale = Locale.getDefault()
    Map parameters = [:]
}
