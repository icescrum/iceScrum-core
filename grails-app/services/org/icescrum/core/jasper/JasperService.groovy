/*
 * Copyright (c) 2026 iceScrum community.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * Minimal replacement for the Grails 2 jasper plugin's JasperService: resolves
 * report templates under /reports (webapp or classpath), compiles .jrxml on
 * demand and exports to the requested format.
 */
package org.icescrum.core.jasper

import net.sf.jasperreports.engine.JRParameter
import net.sf.jasperreports.engine.JasperCompileManager
import net.sf.jasperreports.engine.JasperFillManager
import net.sf.jasperreports.engine.JasperPrint
import net.sf.jasperreports.engine.JasperReport
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource
import net.sf.jasperreports.engine.export.HtmlExporter
import net.sf.jasperreports.engine.export.JRCsvExporter
import net.sf.jasperreports.engine.export.JRPdfExporter
import net.sf.jasperreports.engine.export.JRRtfExporter
import net.sf.jasperreports.engine.export.JRTextExporter
import net.sf.jasperreports.engine.export.JRXmlExporter
import net.sf.jasperreports.engine.export.oasis.JROdsExporter
import net.sf.jasperreports.engine.export.oasis.JROdtExporter
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter
import net.sf.jasperreports.engine.util.JRLoader
import net.sf.jasperreports.export.Exporter
import net.sf.jasperreports.export.SimpleExporterInput
import net.sf.jasperreports.export.SimpleHtmlExporterOutput
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput
import net.sf.jasperreports.export.SimpleWriterExporterOutput
import org.springframework.web.context.ServletContextAware

import jakarta.servlet.ServletContext
import java.util.concurrent.ConcurrentHashMap

class JasperService implements ServletContextAware {

    static transactional = false

    ServletContext servletContext

    private final Map<String, JasperReport> reportCache = new ConcurrentHashMap<>()

    ByteArrayOutputStream generateReport(JasperReportDef reportDef) {
        JasperReport report = loadReport(reportDef)
        Map parameters = new HashMap(reportDef.parameters ?: [:])
        parameters[JRParameter.REPORT_LOCALE] = reportDef.locale
        def dataSource = new JRBeanCollectionDataSource(reportDef.reportData ?: [])
        JasperPrint print = JasperFillManager.fillReport(report, parameters, dataSource)
        def output = new ByteArrayOutputStream()
        exportPrint(print, reportDef.fileFormat, output)
        return output
    }

    private JasperReport loadReport(JasperReportDef reportDef) {
        String key = "${reportDef.folder}/${reportDef.name}"
        return reportCache.computeIfAbsent(key) { k ->
            InputStream compiled = findResource("/${reportDef.folder}/${reportDef.name}.jasper")
            if (compiled) {
                return (JasperReport) JRLoader.loadObject(compiled)
            }
            InputStream source = findResource("/${reportDef.folder}/${reportDef.name}.jrxml")
            if (source) {
                return JasperCompileManager.compileReport(source)
            }
            throw new IllegalArgumentException("Report template not found: ${k}(.jasper|.jrxml)")
        }
    }

    private InputStream findResource(String path) {
        InputStream stream = servletContext?.getResourceAsStream(path)
        return stream ?: getClass().getResourceAsStream(path)
    }

    private void exportPrint(JasperPrint print, JasperExportFormat format, OutputStream output) {
        Exporter exporter
        switch (format) {
            case JasperExportFormat.PDF_FORMAT:
                exporter = new JRPdfExporter()
                exporter.exporterOutput = new SimpleOutputStreamExporterOutput(output)
                break
            case JasperExportFormat.HTML_FORMAT:
                exporter = new HtmlExporter()
                exporter.exporterOutput = new SimpleHtmlExporterOutput(output)
                break
            case JasperExportFormat.XML_FORMAT:
                exporter = new JRXmlExporter()
                exporter.exporterOutput = new SimpleWriterExporterOutput(output)
                break
            case JasperExportFormat.CSV_FORMAT:
                exporter = new JRCsvExporter()
                exporter.exporterOutput = new SimpleWriterExporterOutput(output)
                break
            case JasperExportFormat.XLS_FORMAT: // legacy xls rendered as xlsx
            case JasperExportFormat.XLSX_FORMAT:
                exporter = new JRXlsxExporter()
                exporter.exporterOutput = new SimpleOutputStreamExporterOutput(output)
                break
            case JasperExportFormat.ODT_FORMAT:
                exporter = new JROdtExporter()
                exporter.exporterOutput = new SimpleOutputStreamExporterOutput(output)
                break
            case JasperExportFormat.ODS_FORMAT:
                exporter = new JROdsExporter()
                exporter.exporterOutput = new SimpleOutputStreamExporterOutput(output)
                break
            case JasperExportFormat.DOCX_FORMAT:
                exporter = new JRDocxExporter()
                exporter.exporterOutput = new SimpleOutputStreamExporterOutput(output)
                break
            case JasperExportFormat.RTF_FORMAT:
                exporter = new JRRtfExporter()
                exporter.exporterOutput = new SimpleWriterExporterOutput(output)
                break
            case JasperExportFormat.TEXT_FORMAT:
                exporter = new JRTextExporter()
                exporter.exporterOutput = new SimpleWriterExporterOutput(output)
                break
            default:
                throw new IllegalArgumentException("Unsupported report format: ${format}")
        }
        exporter.exporterInput = new SimpleExporterInput(print)
        exporter.exportReport()
    }
}
