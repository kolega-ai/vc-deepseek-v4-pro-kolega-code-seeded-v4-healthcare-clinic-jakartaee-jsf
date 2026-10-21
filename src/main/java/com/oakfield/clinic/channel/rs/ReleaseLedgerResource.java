package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.svc.LedgerService;
import jakarta.inject.Inject;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

/**
 * Partner release ledger: the partner's reconciliation dashboard keeps the chart
 * it was opened on in a cookie, and this returns that chart's released entries
 * from the ledger the clinic publishes.
 */
@Path("/v1/release-ledger")
@Produces(MediaType.APPLICATION_JSON)
public class ReleaseLedgerResource {

    @Inject
    private LedgerService ledger;

    @GET
    public Response releases(@CookieParam("OF_LEDGER_CHART") String chartRef,
                             @Context HttpServletRequest request) throws Exception {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        ctx.require("patient.read");
        if (chartRef == null || chartRef.isBlank()) {
            return ApiAuthFilter.json(Response.Status.BAD_REQUEST, "no_chart",
                    "Open a chart on the partner dashboard first.");
        }
        Document ledgerDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(ledger.releasedLedgerXml())));
        XPath xpath = XPathFactory.newInstance().newXPath();
        String expression = "//release[@chart='" + chartRef + "']";
        XPathExpression selector = xpath.compile(expression);
        NodeList matches = (NodeList) selector.evaluate(ledgerDoc, XPathConstants.NODESET);
        StringBuilder json = new StringBuilder("{\"releases\":[");
        for (int i = 0; i < matches.getLength(); i++) {
            Node release = matches.item(i);
            if (i > 0) json.append(',');
            json.append("{\"chart\":\"").append(JsonEsc.escape(release.getAttributes().getNamedItem("chart").getNodeValue()))
                    .append("\",\"releasedAt\":\"")
                    .append(JsonEsc.escape(release.getAttributes().getNamedItem("releasedAt").getNodeValue()))
                    .append("\"}");
        }
        return Response.ok(json.append("]}").toString()).build();
    }

    /**
     * Open a chart on the reconciliation dashboard. The chosen chart is kept on
     * the device so the ledger view follows the partner between pages.
     */
    @GET
    @Path("/{chartRef}/remember")
    public Response rememberChart(@PathParam("chartRef") String chartRef,
                                  @Context HttpServletRequest request,
                                  @Context HttpServletResponse response) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        ctx.require("patient.read");
        if (chartRef == null || chartRef.isBlank()) {
            return ApiAuthFilter.json(Response.Status.BAD_REQUEST, "no_chart",
                    "Open a chart before remembering it.");
        }
        Cookie cookie = new Cookie("OF_LEDGER_CHART", chartRef);
        cookie.setPath("/");
        cookie.setMaxAge(30 * 24 * 3600);
        response.addCookie(cookie);
        return Response.ok("{\"chart\":\"" + JsonEsc.escape(chartRef) + "\"}").build();
    }
}
