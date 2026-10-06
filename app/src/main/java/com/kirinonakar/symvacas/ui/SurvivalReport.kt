package com.kirinonakar.symvacas.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal fun survivalStepPoints(curve:JSONArray,index:Int):List<Pair<Double,Double>> {
    val points=mutableListOf(0.0 to 1.0);var previous=1.0
    for(i in 0 until curve.length()) {val row=curve.getJSONArray(i);val time=row.getDouble(0);val value=row.getDouble(index);points.add(time to previous);points.add(time to value);previous=value}
    return points
}

@Composable internal fun SurvivalReport(report:JSONObject,plan:SurvivalPlan?,band:Boolean) {
    val c=LocalInstrument.current;val ko=isKorean()
    fun label(en:String,kr:String)=if(ko)kr else en
    fun number(value:Double)=if(value.isFinite())String.format(Locale.US,"%.5g",value).replace(Regex("(\\.\\d*?)0+(?=e|$)"),"$1").replace(Regex("\\.(?=e|$)"),"") else "—"
    fun value(row:JSONObject,key:String)=if(row.isNull(key))"—" else number(row.optDouble(key,Double.NaN))
    val raw=report.getJSONArray("groups");val groups=List(raw.length()){raw.getJSONObject(it)}
    val names=groups.mapIndexed {i,g->plan?.groups?.getOrNull(i) ?: if(groups.size==1)label("All subjects","전체") else label("Group ","그룹 ")+number(g.getDouble("id"))}
    Text("Kaplan–Meier",style=MaterialTheme.typography.titleSmall)
    groups.forEachIndexed {i,g->Text("${names[i]} · n=${g.getInt("n")}",color=c.curves[i%c.curves.size],fontSize=12.sp)}
    Canvas(Modifier.fillMaxWidth().height(250.dp).testTag("statistics-survival-plot").semantics {contentDescription=if(ko)"Kaplan–Meier 생존곡선과 95% 신뢰구간" else "Kaplan–Meier survival curves and 95% confidence intervals"}) {
        val left=42.dp.toPx();val right=size.width-12.dp.toPx();val top=25.dp.toPx();val bottom=size.height-36.dp.toPx()
        val maxTime=groups.maxOfOrNull {g->g.getJSONArray("curve").let {it.getJSONArray(it.length()-1).getDouble(0)}}?.coerceAtLeast(1.0) ?: 1.0
        fun x(t:Double)=left+(t/maxTime*(right-left)).toFloat()
        fun y(p:Double)=bottom-(p*(bottom-top)).toFloat()
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=c.muted.toArgb();textSize=10.sp.toPx()}
        for(i in 0..4) {
            val py=y(i/4.0);drawLine(c.grid,Offset(left,py),Offset(right,py),1.dp.toPx())
            paint.textAlign=Paint.Align.RIGHT;drawContext.canvas.nativeCanvas.drawText("${i*25}%",left-6.dp.toPx(),py+4.dp.toPx(),paint)
            paint.textAlign=Paint.Align.CENTER;drawContext.canvas.nativeCanvas.drawText(number(maxTime*i/4),x(maxTime*i/4),bottom+17.dp.toPx(),paint)
        }
        paint.textAlign=Paint.Align.LEFT;drawContext.canvas.nativeCanvas.drawText(label("Survival probability","생존확률"),left,12.dp.toPx(),paint)
        paint.textAlign=Paint.Align.CENTER;drawContext.canvas.nativeCanvas.drawText(label("Time","시간"),(left+right)/2,size.height-2.dp.toPx(),paint)
        fun path(points:List<Pair<Double,Double>>)=Path().apply {points.forEachIndexed {i,(t,p)->if(i==0)moveTo(x(t),y(p)) else lineTo(x(t),y(p))}}
        groups.forEachIndexed {i,g->
            val curve=g.getJSONArray("curve");val color=c.curves[i%c.curves.size]
            if(band)drawPath(path(survivalStepPoints(curve,6)+survivalStepPoints(curve,5).reversed()).apply {close()},color.copy(alpha=.13f))
            drawPath(path(survivalStepPoints(curve,4)),color,style=Stroke(2.dp.toPx()))
            for(j in 0 until curve.length()) {val row=curve.getJSONArray(j);if(row.getDouble(3)>0){val px=x(row.getDouble(0));val py=y(row.getDouble(4));val r=3.dp.toPx();drawLine(color,Offset(px-r,py),Offset(px+r,py),1.dp.toPx());drawLine(color,Offset(px,py-r),Offset(px,py+r),1.dp.toPx())}}
        }
    }
    Text(label("Shading: pointwise 95% CI · + censored","음영: 시점별 95% 신뢰구간 · + 중도절단"),fontSize=11.sp,color=c.muted)
    SurvivalTable(listOf(label("Group","그룹"),"n",label("Events","사건"),label("Median","중앙 생존시간")),groups.mapIndexed {i,g->listOf(names[i],g.getInt("n").toString(),g.getInt("events").toString(),if(g.isNull("median"))label("Not reached","미도달") else value(g,"median"))})
    Text(label("Log-rank test","Log-rank 검정"),style=MaterialTheme.typography.titleSmall)
    val lr=report.optJSONObject("logrank")
    Text(if(lr==null)label("Choose at least two groups","비교할 그룹이 2개 이상 필요합니다.") else if(lr.has("error"))label("Unavailable","계산 불가")+" · "+tr(lr.getString("error")) else "χ²=${value(lr,"chi2")} · df=${lr.getInt("df")} · p=${value(lr,"p")}",fontSize=12.sp,color=c.muted)
    report.optJSONObject("cox")?.let {cox->
        Text(label("Cox proportional hazards","Cox 비례위험"),style=MaterialTheme.typography.titleSmall)
        if(cox.has("error"))Text(label("Unavailable","계산 불가")+" · "+tr(cox.getString("error")),fontSize=12.sp,color=c.muted)
        else {
            val coefficients=cox.getJSONArray("coefficients")
            SurvivalTable(listOf(label("Term","변수"),"HR","95% CI","p"),List(coefficients.length()){i->
                val row=coefficients.getJSONObject(i);val term=row.getString("term");val index=term.substringAfter(':').toIntOrNull() ?: -1
                val name=if(term.startsWith("group:"))"${names.getOrNull(index)} / ${names.first()}" else plan?.predictors?.getOrNull(index) ?: term
                val ci=row.optJSONArray("HR CI95")
                listOf(name,value(row,"HR"),if(ci==null)"—" else (0 until ci.length()).joinToString(" – "){number(ci.optDouble(it,Double.NaN))},value(row,"p"))
            })
            val parts=mutableListOf(label("Reference: first group","기준: 첫 그룹"),(if(report.optString("ties")=="efron")"Efron" else "Breslow")+" "+label("ties","동률 처리"))
            if(report.optBoolean("truncation",false))parts.add(label("left truncation","좌측 절단"))
            parts.add(when {
                !report.optBoolean("ph",false)->label("PH test off","PH 검정 생략")
                !cox.has("PH test p")->label("PH test unavailable","PH 검정 계산 불가")
                else->"PH χ²=${value(cox,"PH test chi2")} · p=${value(cox,"PH test p")}"
            })
            Text(parts.joinToString(" · "),fontSize=11.sp,color=c.muted)
        }
    }
}

@Composable private fun SurvivalTable(headers:List<String>,rows:List<List<String>>) {
    val weights=if(headers.getOrNull(1)=="HR")listOf(.22f,.16f,.4f,.22f) else listOf(.28f,.12f,.18f,.42f)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {headers.forEachIndexed {i,text->Text(text,Modifier.weight(weights[i]).padding(horizontal=2.dp,vertical=6.dp),fontSize=11.sp,color=LocalInstrument.current.muted)}}
        rows.forEach {row->Row(Modifier.fillMaxWidth()) {row.forEachIndexed {i,text->Text(text,Modifier.weight(weights[i]).padding(horizontal=2.dp,vertical=5.dp),fontSize=12.sp)}}}
    }
}
