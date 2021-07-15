
package org.omg.dmn.tck.validation;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.omg.dmn.tck.marshaller.TckMarshallingHelper;
import org.omg.dmn.tck.marshaller._20160719.TestCases;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(Parameterized.class)
public class TestCasesFiles {

    static Schema dmnSchema;
    static Schema testCasesSchema;
    static {
        try {
            dmnSchema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(new File("../../TestCases/DMN13.xsd"));
            testCasesSchema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(new File("../../TestCases/testCases.xsd"));
        } catch (SAXException e) {
            throw new RuntimeException("Unable to initialize correctly.", e);
        }
    }

    @Parameters(name = "TestCase {0}")
    public static Iterable<Object[]> data() {
        List<Object[]> testCases = new ArrayList<>();
        File cl2parent = new File("../../TestCases/compliance-level-2");
        FileFilter filenameFilter = pathname -> pathname.isDirectory();
        for (File file : cl2parent.listFiles(filenameFilter)) {
            testCases.add(new Object[]{file.getName(), file});
        }
        File cl3parent = new File("../../TestCases/compliance-level-3");
        for (File file : cl3parent.listFiles(filenameFilter)) {
            testCases.add(new Object[]{file.getName(), file});
        }
        return testCases;
    }

    private String testCaseID;

    private File basedir;
    private XPath xPath = XPathFactory.newInstance().newXPath();
    private XPathExpression inputDataVarMissingTypeRef;
    private DocumentBuilder builder;

    public TestCasesFiles(String testCaseID, File basedir) {
        this.testCaseID = testCaseID;
        this.basedir = basedir;
        try {
            inputDataVarMissingTypeRef = xPath.compile("/*[local-name()='definitions']/*[local-name()='inputData']/*[local-name()='variable' and not(@*[local-name()='typeRef'])]/../@*[local-name()='name']");
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (Exception e) {
            throw new RuntimeException("This JDK does not correctly support XPath", e);
        }
    }

    @Test
    public void testDMNFileIsValid() throws Exception {
        for (File dmnFile : basedir.listFiles((dir, name) -> name.matches(".*\\.dmn$"))) {
            dmnSchema.newValidator().validate(new StreamSource(dmnFile));
            checkInputDataHasTypeRef(dmnFile);
        }
    }

    private void checkInputDataHasTypeRef(File dmnFile) throws Exception {
        Document xmlDocument = builder.parse(dmnFile);
        NodeList xpathResults = (NodeList) inputDataVarMissingTypeRef.evaluate(xmlDocument, XPathConstants.NODESET);
        List<String> problems = new ArrayList<>();
        if (xpathResults.getLength() > 0) {
            for(int i = 0; i< xpathResults.getLength(); i++) {
                Node item = xpathResults.item(i);
                String problem = String.format("%s: InputData node '%s' variable missing typeRef", dmnFile.getName(), item);
                System.err.println(problem);
                problems.add(problem);
            }
        }
        if (!problems.isEmpty()) {
            throw new RuntimeException(problems.stream().collect(Collectors.joining(", "))+". Consider explicity typeRef for the specific test case value or typeRef='Any' if necessary.");
        }
    }

    @Test
    public void testDMNFilesUniqueInNamespace() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Map<String, List<File>> namespaceToFiles = new HashMap<>();
        for (File dmnFile : basedir.listFiles((dir, name) -> name.matches(".*\\.dmn$"))) {
            Document document = builder.parse(dmnFile);
            Element definitions = document.getDocumentElement();
            String dmnNamespace = definitions.getAttribute("namespace"); // DMN specification, Definitions element, namespace attribute;
            namespaceToFiles.computeIfAbsent(dmnNamespace, x -> new ArrayList<>()).add(dmnFile);
        }
        for (Entry<String, List<File>> e : namespaceToFiles.entrySet()) {
            if (e.getValue().size() > 1) {
                throw new RuntimeException("There are 2 or more files with the same DMN namespace: " +
                                           e.getKey() +
                                           "\nin the folder: " +
                                           basedir +
                                           "\nthese files are: " +
                                           e.getValue());
            }
        }
    }

    @Test
    public void testCaseFileIsValid() throws Exception {
        for (File dmnFile : basedir.listFiles((dir, name) -> name.matches(".*\\.xml$"))) {
            testCasesSchema.newValidator().validate(new StreamSource(dmnFile));

            TestCases testCases = TckMarshallingHelper.load(new FileInputStream(dmnFile));
            Map<String, Long> idCounting = testCases.getTestCase()
                                                    .stream()
                                                    .map(TestCases.TestCase::getId)
                                                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            for (Entry<String, Long> kv : idCounting.entrySet()) {
                assertThat("The id '" + kv.getKey() + "' is occuring more than one time in the file " + dmnFile, kv.getValue(), is(1L));
            }
        }
    }
}
