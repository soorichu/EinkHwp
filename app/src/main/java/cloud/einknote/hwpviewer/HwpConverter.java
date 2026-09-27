package cloud.einknote.hwpviewer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.Control;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.reader.HWPReader;
import android.util.Base64;
import android.util.Log;


public class HwpConverter {

    public static String toEinkHtml(File hwpFile, String customFontPath) throws Exception {
        HWPFile hwp;
        try (FileInputStream fis = new FileInputStream(hwpFile)) {
            hwp = HWPReader.fromInputStream(fis);
        }

        StringBuilder bodySb = new StringBuilder();
        ArrayList<Section> sectionList = hwp.getBodyText().getSectionList();
        for (Section section : sectionList) {
            for (Paragraph p : section.getParagraphs()) {
                parseParagraph(p, bodySb);
            }
        }

        // 사용자 폰트가 지정된 경우 @font-face 생성
        StringBuilder fontFaceCss = new StringBuilder();
        String fontFamilyName = "CustomUserFont, serif, sans-serif";

        if (customFontPath != null && !customFontPath.trim().isEmpty()) {
            File fontFile = new File(customFontPath);
            if (fontFile.exists() && fontFile.canRead()) {
                try (FileInputStream fontFis = new FileInputStream(fontFile)) {
                    byte[] fontBytes = new byte[(int) fontFile.length()];
                    fontFis.read(fontBytes);
                    String fontBase64 = Base64.encodeToString(fontBytes, Base64.NO_WRAP);
                    fontFaceCss.append("@font-face {\n")
                            .append("    font-family: 'CustomUserFont';\n")
                            .append("    src: url('data:font/truetype;charset=utf-8;base64,").append(fontBase64).append("') format('truetype');\n")
                            .append("}\n");
                }
            }
        }

        // 10pt를 96 DPI 화면 기준으로 환산: int((10.0 / 72.0) * 96) + 1 = 14
        int baseFontSize = (int) ((10.0 / 72.0) * 96) + 1;

        return "<!DOCTYPE html>\n" +
                "<html lang=\"ko\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=no\">\n" +
                "    <style>\n" +
                fontFaceCss.toString() +
                "        html, body {\n" +
                "            margin: 0;\n" +
                "            padding: 0;\n" +
                "            background-color: #FFFFFF !important;\n" +
                "            color: #000000 !important;\n" +
                "            font-family: " + fontFamilyName + " !important;\n" +
                "            overflow-x: hidden;\n" +
                "            overflow-y: auto;\n" +
                "        }\n" +
                "        body {\n" +
                "            box-sizing: border-box;\n" +
                "            padding: 20px 16px 80px 16px;\n" +
                "            font-size: " + baseFontSize + "px;\n" +
                "            line-height: 1.8;\n" +
                "            word-break: keep-all;\n" +
                "            text-align: justify;\n" +
                "        }\n" +
                "        p {\n" +
                "            margin: 0 0 1.1em 0;\n" +
                "            text-indent: 0.5em;\n" +
                "            color: #000000 !important;\n" +
                "            font-family: inherit !important;\n" +
                "        }\n" +
                "        .table-container {\n" +
                "            width: 100%;\n" +
                "            max-width: 100%;\n" +
                "            overflow-x: auto;\n" +
                "            margin: 1.2em 0;\n" +
                "        }\n" +
                "        table {\n" +
                "            width: 100%;\n" +
                "            max-width: 100%;\n" +
                "            table-layout: auto;\n" +
                "            border-collapse: collapse;\n" +
                "            color: #000000 !important;\n" +
                "        }\n" +
                "        th, td {\n" +
                "            border: 1.5px solid #000000;\n" +
                "            padding: 6px 8px;\n" +
                "            font-size: 0.85em;\n" +
                "            word-break: break-word;\n" +
                "            color: #000000 !important;\n" +
                "            font-family: inherit !important;\n" +
                "        }\n" +
                "        img {\n" +
                "            max-width: 100% !important;\n" +
                "            max-height: 85vh !important;\n" +
                "            height: auto;\n" +
                "            display: block;\n" +
                "            margin: 1.2em auto;\n" +
                "            object-fit: contain;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                bodySb.toString() +
                "</body>\n" +
                "</html>";
    }

    public static int convertPtToSp(double pt) {
        return (int) ((pt / 72.0) * 96) + 1;
    }

    private static void parseParagraph(Paragraph p, StringBuilder sb) throws UnsupportedEncodingException {

        if (p.getText() != null) {
            String text = p.getNormalString();
            if (text != null && !text.trim().isEmpty()) {
                sb.append("<p>").append(escapeHtml(text)).append("</p>\n");
            }
        }

        if (p.getControlList() != null) {
            for (Control ctrl : p.getControlList()) {
                if (ctrl instanceof ControlTable) {
                    renderTable((ControlTable) ctrl, sb);
                }
            }
        }
    }


    private static void renderTable(ControlTable table, StringBuilder sb) throws UnsupportedEncodingException {
        sb.append("<div class=\"table-container\">\n");
        sb.append("<table>\n");

        // 1. 표의 전체 행 수와 최대 열 수를 구합니다.
        int rowCount = table.getRowList().size();
        int maxColCount = 0;
        for (Row row : table.getRowList()) {
            if (row.getCellList().size() > maxColCount) {
                maxColCount = row.getCellList().size();
            }
        }

        // 2. 병합된 '더미 셀(Dummy Cell)'을 건너뛰기 위한 체크 맵 생성
        boolean[][] skipMap = new boolean[rowCount][maxColCount];

        for (int r = 0; r < rowCount; r++) {
            Row row = table.getRowList().get(r);
            sb.append("  <tr>\n");

            for (int c = 0; c < row.getCellList().size(); c++) {
                Cell cell = row.getCellList().get(c);

                // HWP 셀 객체에서 병합 정보 읽어오기
                int colSpan = cell.getListHeader().getColSpan();
                int rowSpan = cell.getListHeader().getRowSpan();

                // 4. 현재 셀이 병합을 사용한다면, 잡아먹은 영역을 skipMap에 'true'로 마킹
                for (int rs = 0; rs < rowSpan; rs++) {
                    for (int cs = 0; cs < colSpan; cs++) {
                        if (rs == 0 && cs == 0) continue; // 자기 자신은 마킹 제외

                        int nextR = r + rs;
                        int nextC = c + cs;
                        if (nextR < rowCount && nextC < maxColCount) {
                            skipMap[nextR][nextC] = true;
                        }
                    }
                }

                // 5. 병합 정보(colspan, rowspan)를 HTML 태그에 적용
                sb.append("    <td");
                if (colSpan > 1) sb.append(" colspan=\"").append(colSpan).append("\"");
                if (rowSpan > 1) sb.append(" rowspan=\"").append(rowSpan).append("\"");
                sb.append(">");

                // 셀 안의 문단(텍스트) 처리
                for (Paragraph cp : cell.getParagraphList()) {
                    if (cp.getText() != null) {
                        String cellText = cp.getNormalString();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            sb.append(escapeHtml(cellText)).append(" ");
                        }
                    }
                }
                sb.append("</td>\n");
            }
            sb.append("  </tr>\n");
        }
        sb.append("</table>\n");
        sb.append("</div>\n");

     //   Log.d("sb",  sb.toString());
    }
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static void exportToEpub(File hwpFile, File destEpub) throws Exception {
        String title = hwpFile.getName().replaceAll("(?i)\\.hwp$", "");
        String rawHtml = toEinkHtml(hwpFile, null);
        String xhtml = rawHtml.replace("<!DOCTYPE html>", "")
                .replace("<html lang=\"ko\">", "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"ko\">");

        String containerXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
                "  <rootfiles>\n" +
                "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n" +
                "  </rootfiles>\n" +
                "</container>";

        String contentOpf = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"BookID\">\n" +
                "  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
                "    <dc:title>" + escapeHtml(title) + "</dc:title>\n" +
                "    <dc:language>ko</dc:language>\n" +
                "    <dc:identifier id=\"BookID\">urn:uuid:" + System.currentTimeMillis() + "</dc:identifier>\n" +
                "  </metadata>\n" +
                "  <manifest>\n" +
                "    <item id=\"content\" href=\"section.xhtml\" media-type=\"application/xhtml+xml\"/>\n" +
                "  </manifest>\n" +
                "  <spine>\n" +
                "    <itemref idref=\"content\"/>\n" +
                "  </spine>\n" +
                "</package>";

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(destEpub))) {
            byte[] mimeBytes = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry mimeEntry = new ZipEntry("mimetype");
            mimeEntry.setMethod(ZipEntry.STORED);
            mimeEntry.setSize(mimeBytes.length);
            mimeEntry.setCompressedSize(mimeBytes.length);
            CRC32 crc = new CRC32();
            crc.update(mimeBytes);
            mimeEntry.setCrc(crc.getValue());

            zip.putNextEntry(mimeEntry);
            zip.write(mimeBytes);
            zip.closeEntry();

            writeZipEntry(zip, "META-INF/container.xml", containerXml);
            writeZipEntry(zip, "OEBPS/content.opf", contentOpf);
            writeZipEntry(zip, "OEBPS/section.xhtml", xhtml);
        }
    }

    private static void writeZipEntry(ZipOutputStream zip, String path, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }
}