package com.example.calcmax.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.*
import com.example.calcmax.calculator.CalculatorModel
import com.example.calcmax.calculator.ExchangeRepository
import com.example.calcmax.math.Money
import com.example.calcmax.math.RateTable
import com.example.calcmax.ui.theme.LocalInstrument
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Currency
import java.util.Date

private fun decimalInput(text:String):BigDecimal {require(text.length<=128);return text.replace(",","").toBigDecimal()}
@Composable fun TipScreen() {
    var bill by rememberSaveable{mutableStateOf("100")}
    var percent by rememberSaveable{mutableStateOf("15")}
    var tax by rememberSaveable{mutableStateOf("0")}
    var people by rememberSaveable{mutableStateOf("1")}
    var currency by rememberSaveable{mutableStateOf("USD")}
    val places=if(currency in listOf("KRW","JPY"))0 else 2
    val result=runCatching{Money.tip(decimalInput(bill),decimalInput(percent),decimalInput(tax),people.toInt(),places)}
    Panel("Tip calculator","Tip on the bill before tax · totals update as you type") {
        Choices(listOf("USD","KRW","EUR","JPY","GBP"),currency,{currency=it})
        Field(bill,"Bill before tax ($currency)",Modifier.fillMaxWidth()){bill=it}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Field(percent,"Tip %",Modifier.weight(1f)){percent=it};Field(tax,"Tax %",Modifier.weight(1f)){tax=it}}
        Choices(listOf("0","5","10","15","18","20","25"),percent,{percent=it})
        Field(people,"Number of people",Modifier.fillMaxWidth()){people=it}
        result.getOrNull()?.let {r->
            Text("Total  ${Money.format(r.total)} $currency",style=MaterialTheme.typography.headlineMedium)
            Text("Tip  ${Money.format(r.tip)} $currency   ·   Tax  ${Money.format(r.tax)} $currency")
            HorizontalDivider()
            Text("Per person  ${Money.format(r.share)} $currency",style=MaterialTheme.typography.titleLarge)
            if(r.extraPeople>0)Text("${r.extraPeople} ${if(r.extraPeople==1)"person pays" else "people pay"} ${Money.format(r.extraShare)} $currency; the others pay ${Money.format(r.share)} $currency. This keeps the split equal to the total.",fontSize=12.sp)
        } ?: Text("Enter valid non-negative amounts and a whole number of people.",color=LocalInstrument.current.danger)
    }
}

@Composable fun CurrencyScreen(m:CalculatorModel) {
    var amount by rememberSaveable{mutableStateOf("100")}
    var from by rememberSaveable{mutableStateOf("USD")}
    var to by rememberSaveable{mutableStateOf("KRW")}
    var manual by rememberSaveable{mutableStateOf(false)}
    var manualRate by rememberSaveable{mutableStateOf("")}
    val uri=LocalUriHandler.current
    val table=m.exchangeRates
    LaunchedEffect(manual){if(!manual)m.loadExchangeRates()}
    LaunchedEffect(manual,table?.fetchedMillis){
        if(!manual&&table!=null){delay((table.fetchedMillis+RateTable.TTL-System.currentTimeMillis()).coerceAtLeast(60000));m.loadExchangeRates()}
    }
    val rate=runCatching{if(manual)decimalInput(manualRate).also{require(it.signum()>0)} else table?.rate(from,to,m.precision.coerceAtLeast(16)) ?: error("No cached rates")}
    val converted=runCatching{decimalInput(amount).multiply(rate.getOrThrow(),MathContext(m.precision.coerceAtLeast(16),RoundingMode.HALF_EVEN))}
    Panel("Currency converter","Latest online reference rates with an offline cache. These are indicative daily rates, not trading quotes.") {
        Choices(listOf("Online / cache","Manual"),if(manual)"Manual" else "Online / cache",{manual=it=="Manual"})
        Field(amount,"Amount",Modifier.fillMaxWidth()){amount=it}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Field(from,"From (ISO code)",Modifier.weight(1f)){from=it.uppercase().take(4);manualRate=""}
            Field(to,"To (ISO code)",Modifier.weight(1f)){to=it.uppercase().take(4);manualRate=""}
        }
        Choices(listOf("KRW","USD","EUR","JPY","CNY","GBP","CAD","AUD","CHF"),to,{to=it;manualRate=""})
        SmallAction("Swap currencies") {
            val previous=from;from=to;to=previous
            if(manual)manualRate=runCatching{Money.format(BigDecimal.ONE.divide(decimalInput(manualRate),MathContext.DECIMAL128))}.getOrDefault("")
        }
        converted.getOrNull()?.let {value->
            Text("≈ ${Money.format(value.round(MathContext(m.precision,RoundingMode.HALF_EVEN)))} $to",style=MaterialTheme.typography.headlineMedium)
            Text("1 $from = ${Money.format(rate.getOrThrow().round(MathContext(minOf(m.precision,12))))} $to",fontSize=13.sp)
        } ?: Text(if(manual)"Enter a positive conversion rate." else if(table==null)"A saved rate or manual rate is needed." else "Check the currency codes.",color=LocalInstrument.current.muted)
        if(manual)Field(manualRate,"1 $from = ? $to",Modifier.fillMaxWidth()){manualRate=it}
        HorizontalDivider()
        if(manual)Text("Manual rate · not an online quote",fontSize=12.sp)
        else {
            Text(if(m.exchangeBusy)"Updating rates…" else m.exchangeStatus,fontSize=12.sp)
            table?.let {
                val date=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT)
                Text("Rate reference time: ${date.format(Date(it.referenceMillis))} (${java.util.TimeZone.getDefault().id})",fontSize=12.sp)
                Text("Saved on device: ${date.format(Date(it.fetchedMillis))}",fontSize=11.sp)
                Text("Next update: ${date.format(Date(it.fetchedMillis+RateTable.TTL))}",fontSize=11.sp)
            }
            TextButton(onClick={m.loadExchangeRates()},enabled=!m.exchangeBusy&&(table==null||table.due(System.currentTimeMillis()))){Text(if(table!=null&&!table.due(System.currentTimeMillis()))"24-hour cache is current" else "Update rates")}
        }
        Text("Rates By Exchange Rate API",Modifier.clickable{uri.openUri(ExchangeRepository.SOURCE)},color=LocalInstrument.current.accent,fontSize=12.sp)
    }
}
