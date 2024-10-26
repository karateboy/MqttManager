package models
import com.itextpdf.text.{Document, PageSize}
import com.itextpdf.text.pdf.PdfWriter
import com.itextpdf.tool.xml.{XMLWorker, XMLWorkerFontProvider, XMLWorkerHelper}
import com.itextpdf.tool.xml.html.{CssAppliersImpl, Tags}
import com.itextpdf.tool.xml.parser.XMLParser
import com.itextpdf.tool.xml.pipeline.css.CssResolverPipeline
import com.itextpdf.tool.xml.pipeline.end.PdfWriterPipeline
import com.itextpdf.tool.xml.pipeline.html.{HtmlPipeline, HtmlPipelineContext}
import play.api.Application

import javax.inject._
/**
 * @author user
 */
class PdfUtility @Inject()(app:Application){
  val CSS_ROOT = "/public/"
  
  def creatPdfWithReportHeader(title:String, content:play.twirl.api.HtmlFormat.Appendable)={
    val html = views.html.reportTemplate(title, content)
    createPdf(html.toString)
  }
  
  def creatPdfWithReportHeaderP(title:String, content:play.twirl.api.HtmlFormat.Appendable)={
    val html = views.html.reportTemplate(title, content)
    createPdf(html.toString, false)
  }
  
  def createPdf(htmlInput: String, landscape:Boolean=true) = {

    //debug
    import java.io.FileOutputStream
    //val outs = new FileOutputStream("D:/temp/output.html")
    //outs.write(htmlInput.getBytes(Charset.forName("UTF-8")))
    //outs.close()
    
    // step 1
    val document =
      if(landscape)
        new Document(PageSize.A4.rotate());
      else
        new Document(PageSize.A4);
    
    // step 2
    import java.io._
    import java.nio.charset.Charset

    val tempFile = File.createTempFile("report", ".pdf")
    val writer = PdfWriter.getInstance(document, new FileOutputStream(tempFile));

    // step 3
    document.open();

    // step 4

    // CSS
    val cssResolver =
                XMLWorkerHelper.getInstance().getDefaultCssResolver(false);
    val bootstrapCss = XMLWorkerHelper.getCSS(new FileInputStream(app.path + CSS_ROOT +"css/bootstrap.min.css"))
    cssResolver.addCss(bootstrapCss)
    
    //val styleCss = XMLWorkerHelper.getCSS(new FileInputStream(current.path + CSS_ROOT +"css/style.css"))
    //cssResolver.addCss(styleCss)

    val aqmCss = XMLWorkerHelper.getCSS(new FileInputStream(app.path + CSS_ROOT +"css/aqm.css"))
    cssResolver.addCss(aqmCss)

    // HTML
    val fontProvider = new XMLWorkerFontProvider();
    val cssAppliers = new CssAppliersImpl(fontProvider);
    val htmlContext = new HtmlPipelineContext(cssAppliers);
    htmlContext.setTagFactory(Tags.getHtmlTagProcessorFactory());

    // Pipelines
    val pdf = new PdfWriterPipeline(document, writer);
    val html = new HtmlPipeline(htmlContext, pdf);
    val css = new CssResolverPipeline(cssResolver, html);

    // XML Worker
    val worker = new XMLWorker(css, true);
    val p = new XMLParser(worker);
    val charSet = Charset.forName("UTF-8")
    p.parse(new ByteArrayInputStream(htmlInput.getBytes(charSet)), charSet)

    // step 5
    document.close()

    tempFile
  }
}