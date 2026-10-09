package com.kirinonakar.symvacas.ui

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

private const val MAX_XLSX_UNCOMPRESSED=64L*1024*1024
private const val RELATIONSHIP_NAMESPACE="http://schemas.openxmlformats.org/officeDocument/2006/relationships"

internal data class StatisticsXlsxSheet(val name:String,val preview:StatisticsCsvImport)
internal data class StatisticsXlsxWorkbook(val sheets:List<StatisticsXlsxSheet>)

private fun xlsxXml(bytes:ByteArray):XmlPullParser=Xml.newPullParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true)
    runCatching {setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false)}
    setInput(ByteArrayInputStream(bytes),"UTF-8")
}

private fun readXlsxEntries(bytes:ByteArray):Map<String,ByteArray> {
    require(bytes.size.toLong()<=MAX_XLSX_UNCOMPRESSED) {"XLSX file is too large"}
    val wanted=mutableSetOf("xl/workbook.xml","xl/_rels/workbook.xml.rels","xl/sharedStrings.xml","xl/styles.xml")
    val entries=linkedMapOf<String,ByteArray>();var expanded=0L
    ZipInputStream(ByteArrayInputStream(bytes)).use {zip->
        while(true) {
            val entry=zip.nextEntry?:break
            if(!entry.isDirectory) {
                val keep=entry.name in wanted||entry.name.startsWith("xl/worksheets/")&&entry.name.endsWith(".xml")
                val output=if(keep)ByteArrayOutputStream() else null;val buffer=ByteArray(8192)
                while(true) {
                    val count=zip.read(buffer);if(count<0)break
                    expanded+=count;require(expanded<=MAX_XLSX_UNCOMPRESSED) {"XLSX file is too large"}
                    output?.write(buffer,0,count)
                }
                if(output!=null)entries[entry.name]=output.toByteArray()
            }
            zip.closeEntry()
        }
    }
    return entries
}

private data class XlsxWorksheetRef(val name:String,val relationshipId:String)

private fun normalizeXlsxTarget(target:String):String {
    val parts=mutableListOf<String>()
    (if(target.startsWith('/'))target.drop(1) else "xl/$target").split('/').forEach {part->when(part){".."->if(parts.isNotEmpty())parts.removeAt(parts.lastIndex);".",""->Unit;else->parts+=part}}
    return parts.joinToString("/")
}

private fun xlsxWorksheetList(entries:Map<String,ByteArray>):List<Pair<String,String>> {
    val workbook=entries["xl/workbook.xml"]?:error("XLSX is missing workbook.xml")
    val parser=xlsxXml(workbook);val sheets=mutableListOf<XlsxWorksheetRef>()
    while(parser.eventType!=XmlPullParser.END_DOCUMENT) {
        if(parser.eventType==XmlPullParser.START_TAG&&parser.name=="sheet") {
            val relationId=parser.getAttributeValue(RELATIONSHIP_NAMESPACE,"id")?:parser.getAttributeValue(null,"r:id")
            if(relationId!=null)sheets+=XlsxWorksheetRef(parser.getAttributeValue(null,"name")?:"Sheet ${sheets.size+1}",relationId)
        }
        parser.next()
    }
    val relations=entries["xl/_rels/workbook.xml.rels"]?:return if(entries.containsKey("xl/worksheets/sheet1.xml"))listOf("Sheet 1" to "xl/worksheets/sheet1.xml") else emptyList()
    val relParser=xlsxXml(relations);val targets=mutableMapOf<String,String>()
    while(relParser.eventType!=XmlPullParser.END_DOCUMENT) {
        if(relParser.eventType==XmlPullParser.START_TAG&&relParser.name=="Relationship") {
            val id=relParser.getAttributeValue(null,"Id");val target=relParser.getAttributeValue(null,"Target");val type=relParser.getAttributeValue(null,"Type").orEmpty()
            if(id!=null&&target!=null&&type.endsWith("/worksheet"))targets[id]=normalizeXlsxTarget(target)
        }
        relParser.next()
    }
    val result=sheets.mapNotNull {sheet->targets[sheet.relationshipId]?.let {sheet.name to it}}
    return result.ifEmpty {if(entries.containsKey("xl/worksheets/sheet1.xml"))listOf("Sheet 1" to "xl/worksheets/sheet1.xml") else emptyList()}
}

private fun xlsxSharedStrings(bytes:ByteArray?):List<String> {
    if(bytes==null)return emptyList()
    val parser=xlsxXml(bytes);val values=mutableListOf<String>();var inside=false;var current=StringBuilder()
    while(parser.eventType!=XmlPullParser.END_DOCUMENT) {
        when {
            parser.eventType==XmlPullParser.START_TAG&&parser.name=="si"->{inside=true;current=StringBuilder()}
            parser.eventType==XmlPullParser.START_TAG&&parser.name=="t"&&inside->current.append(parser.nextText())
            parser.eventType==XmlPullParser.END_TAG&&parser.name=="si"->{values+=current.toString();inside=false}
        }
        parser.next()
    }
    return values
}

private data class XlsxStyles(val dateStyles:List<Boolean>) {
    fun isDate(index:Int)=dateStyles.getOrNull(index)==true
}

private fun xlsxStyles(bytes:ByteArray?):XlsxStyles {
    if(bytes==null)return XlsxStyles(emptyList())
    val parser=xlsxXml(bytes);val formats=mutableMapOf<Int,String>();val cellStyles=mutableListOf<Int>()
    var inCellFormats=false
    while(parser.eventType!=XmlPullParser.END_DOCUMENT) {
        if(parser.eventType==XmlPullParser.START_TAG)when(parser.name) {
            "numFmt"->{val id=parser.getAttributeValue(null,"numFmtId")?.toIntOrNull();val code=parser.getAttributeValue(null,"formatCode");if(id!=null&&code!=null)formats[id]=code}
            "cellXfs"->inCellFormats=true
            "xf"->if(inCellFormats)cellStyles+=parser.getAttributeValue(null,"numFmtId")?.toIntOrNull()?:0
        } else if(parser.eventType==XmlPullParser.END_TAG&&parser.name=="cellXfs")inCellFormats=false
        parser.next()
    }
    val builtIn=setOf(14,15,16,17,18,19,20,21,22,27,28,29,30,31,32,33,34,35,36,50,51,52,53,54,55,56,57,58)
    val datePattern=Regex("[yd]",RegexOption.IGNORE_CASE)
    val dateStyles=cellStyles.map {id->
        val format=formats[id].orEmpty().replace(Regex("\"[^\"]*\"|\\\\.|_.|\\*."),"").replace(Regex("\\[[^]]*]"),"")
        id in builtIn||datePattern.containsMatchIn(format)
    }
    return XlsxStyles(dateStyles)
}

private fun xlsxCellColumn(reference:String?):Int {
    val letters=reference.orEmpty().takeWhile(Char::isLetter);if(letters.isEmpty())return -1
    return letters.uppercase().fold(0) {number,char->number*26+char.code-'A'.code+1}-1
}

private fun excelSerialDate(value:String):String? {
    val serial=value.toDoubleOrNull()?.takeIf {it>=1.0&&it<=2_958_465.0}?:return null
    val whole=kotlin.math.floor(serial).toLong()-(if(serial>=60)1 else 0)
    return runCatching {LocalDate.of(1899,12,31).plusDays(whole).toString()}.getOrNull()
}

private fun readXlsxCell(parser:XmlPullParser,type:String,style:Int,shared:List<String>,styles:XlsxStyles):String {
    var value="";val inline=StringBuilder()
    while(true) {
        when(parser.next()) {
            XmlPullParser.START_TAG->when(parser.name) {
                "v"->value=parser.nextText()
                "t"->inline.append(parser.nextText())
            }
            XmlPullParser.END_TAG->if(parser.name=="c")break
            XmlPullParser.END_DOCUMENT->break
        }
    }
    return when(type) {
        "inlineStr"->inline.toString()
        "s"->shared.getOrNull(value.toIntOrNull()?:-1).orEmpty()
        "b"->if(value=="1")"TRUE" else "FALSE"
        else->if(styles.isDate(style))excelSerialDate(value)?:value else value
    }
}

private fun previewXlsxWorksheet(bytes:ByteArray,shared:List<String>,styles:XlsxStyles,removeComputationLimit:Boolean=false):StatisticsCsvImport {
    val parser=xlsxXml(bytes);val rows=mutableListOf<List<String>>();var current:MutableMap<Int,String>?=null
    while(parser.eventType!=XmlPullParser.END_DOCUMENT) {
        when {
            parser.eventType==XmlPullParser.START_TAG&&parser.name=="row"->current=linkedMapOf()
            parser.eventType==XmlPullParser.START_TAG&&parser.name=="c"&&current!=null->{
                val index=xlsxCellColumn(parser.getAttributeValue(null,"r"))
                val type=parser.getAttributeValue(null,"t").orEmpty();val style=parser.getAttributeValue(null,"s")?.toIntOrNull()?:0
                if(index>=100)error("Use up to 100 XLSX data columns")
                val value=readXlsxCell(parser,type,style,shared,styles)
                if(index>=0)current?.set(index,value)
            }
            parser.eventType==XmlPullParser.END_TAG&&parser.name=="row"->{
                val row=current.orEmpty();if(row.values.any(String::isNotBlank)) {
                    if(!removeComputationLimit&&rows.size>=5000)error("Use up to 5000 XLSX data rows")
                    rows+=List((row.keys.maxOrNull()?:-1)+1){row[it].orEmpty().trim()}
                }
                current=null
            }
        }
        parser.next()
    }
    val header=if(rows.isNotEmpty())statisticsHasHeader(rows) else false
    return StatisticsCsvImport(rows,header,rows.maxOfOrNull(List<String>::size)?:0)
}

internal fun previewStatisticsXlsx(bytes:ByteArray,removeComputationLimit:Boolean=false):StatisticsXlsxWorkbook {
    val entries=readXlsxEntries(bytes)
    val worksheets=xlsxWorksheetList(entries)
    require(worksheets.isNotEmpty()) {"XLSX workbook does not contain any worksheets"}
    val shared=xlsxSharedStrings(entries["xl/sharedStrings.xml"]);val styles=xlsxStyles(entries["xl/styles.xml"])
    val sheets=worksheets.mapNotNull {(name,path)->entries[path]?.let {StatisticsXlsxSheet(name,previewXlsxWorksheet(it,shared,styles,removeComputationLimit))}}
    require(sheets.any {it.preview.columnCount>0}) {"The XLSX workbook has no data sheets"}
    return StatisticsXlsxWorkbook(sheets)
}
