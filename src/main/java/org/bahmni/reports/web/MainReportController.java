package org.bahmni.reports.web;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.constant.PageType;
import net.sf.dynamicreports.report.exception.DRException;
import org.apache.log4j.Logger;
import org.bahmni.reports.BahmniReportsProperties;
import org.bahmni.reports.filter.JasperResponseConverter;
import org.bahmni.reports.model.AllDatasources;
import org.bahmni.reports.model.Report;
import org.bahmni.reports.model.Reports;
import org.bahmni.reports.template.BaseReportTemplate;
import org.bahmni.webclients.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

import static net.sf.dynamicreports.report.builder.DynamicReports.report;

@Controller
public class MainReportController {

    private static final Logger logger = Logger.getLogger(MainReportController.class);
    private JasperResponseConverter converter;
    private BahmniReportsProperties bahmniReportsProperties;
    private AllDatasources allDatasources;
    private HttpClient httpClient;

    @Autowired
    public MainReportController(JasperResponseConverter converter,
                                BahmniReportsProperties bahmniReportsProperties,
                                AllDatasources allDatasources, HttpClient httpClient) {
        this.converter = converter;
        this.bahmniReportsProperties = bahmniReportsProperties;
        this.allDatasources = allDatasources;
        this.httpClient = httpClient;
    }

    //TODO: Better way to handle the response.
    @RequestMapping(value = "/report", method = RequestMethod.GET)
    public void getReport(HttpServletRequest request, HttpServletResponse response) {
        Connection connection = null;
        ArrayList<AutoCloseable> resources = new ArrayList<>();
        try {
            String startDate = request.getParameter("startDate");
            String endDate = request.getParameter("endDate");
            String reportName = URLDecoder.decode(request.getParameter("name"), "UTF-8");
            String responseType = request.getParameter("responseType");
            String macroTemplateLocation = request.getParameter("macroTemplateLocation");
            PageType pageType = "A3".equals(request.getParameter("paperSize")) ? PageType.A3 : PageType.A4;
            String appName = request.getParameter("appName");
            String configFilePath = (appName != null) ? "/var/www/bahmni_config/openmrs/apps/" + appName + "/reports.json" : bahmniReportsProperties.getConfigFilePath();
            Report report = Reports.find(reportName, configFilePath);
            report.setHttpClient(httpClient);
            BaseReportTemplate reportTemplate = report.getTemplate(bahmniReportsProperties);
            connection = allDatasources.getConnectionFromDatasource(reportTemplate);
            JasperReportBuilder jasperReport = report();
            jasperReport = new ReportHeader().add(jasperReport, reportName, startDate, endDate);

            JasperReportBuilder reportBuilder = reportTemplate.build(connection, jasperReport, report, startDate, endDate, resources,
                    pageType);

            convertToResponse(responseType, reportBuilder, response, reportName, macroTemplateLocation, bahmniReportsProperties.getMacroTemplatesTempDirectory());
        } catch (Throwable e) {
            logger.error("Error running report", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try {
                String errorMessage = e.getMessage();
                String content = "<h2>Incorrect Configuration</h2><h3>" + errorMessage +"</h3>";
                response.setContentLength(content.length());
                response.setContentType("text/html");
                response.getOutputStream().write(content.getBytes());
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } finally {
            try {
                response.flushBuffer();
                response.getOutputStream().close();
                if (null != connection && !connection.getAutoCommit()) {
                    connection.rollback();
                }
            } catch (IOException e) {
                logger.error(e);
            } catch (SQLException e) {
                logger.error(e);
            }

            for (AutoCloseable resource : resources) {
                try {
                    if (resource != null) {
                        resource.close();
                    }
                } catch (Exception e) {
                    logger.error("Could not close resource.", e);
                }
            }

            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (Exception e) {
                logger.error("Could not close connection.", e);
            }
        }
    }

    private void convertToResponse(String responseType, JasperReportBuilder reportBuilder, HttpServletResponse response, String fileName, String macroTemplateLocation, String macroTemplatesTempDirectory)
            throws Exception {
        try {
            converter.convert(responseType, reportBuilder, response, fileName, macroTemplateLocation, macroTemplatesTempDirectory);
        } catch (DRException | IOException e) {
            logger.error("Could not convert response", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String errorMessage = e.getMessage();
            String content = "<h2>Incorrect Configuration</h2><h3>" + (errorMessage.substring(errorMessage.indexOf(':') + 2) +"</h3>");
            response.setContentLength(content.length());
            response.setContentType("text/html");
            response.getOutputStream().write(content.getBytes());
        }
    }
}
