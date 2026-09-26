package com.kirinonakar.calcmax.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.*
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.calculator.ExchangeRepository
import com.kirinonakar.calcmax.math.Money
import com.kirinonakar.calcmax.math.RateTable
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

private fun decimalInput(text:String):BigDecimal {require(text.length<=128);return text.replace(",","").toBigDecimal()}
/** English full name of an ISO 4217 currency code; null when the code is unknown. */
private fun currencyName(code:String):String?=runCatching{Currency.getInstance(code).getDisplayName(Locale.ENGLISH)}.getOrNull()
/** Caps a displayed money amount at six decimal places and drops trailing zeros. */
private fun displayAmount(value:BigDecimal)=value.setScale(6,RoundingMode.HALF_EVEN).stripTrailingZeros()
@Composable fun TipScreen() {
    var bill by rememberSaveable{mutableStateOf("100")}
    var percent by rememberSaveable{mutableStateOf("15")}
    var tipAmount by rememberSaveable{mutableStateOf("")}
    var tipIsAmount by rememberSaveable{mutableStateOf(false)}
    var tax by rememberSaveable{mutableStateOf("0")}
    var people by rememberSaveable{mutableStateOf("1")}
    val places=2
    val result=runCatching{
        if(tipIsAmount)Money.tipFromAmount(decimalInput(bill),decimalInput(tipAmount),decimalInput(tax),people.toInt(),places)
        else Money.tip(decimalInput(bill),decimalInput(percent),decimalInput(tax),people.toInt(),places)
    }
    Panel("Tip calculator","") {
        Field(bill,"Bill before tax",Modifier.fillMaxWidth()){bill=it}
        Choices(listOf("Tip %","Tip amount"),if(tipIsAmount)"Tip amount" else "Tip %",{tipIsAmount=it=="Tip amount"})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Field(percent,"Tip %",Modifier.weight(1f),enabled=!tipIsAmount){percent=it}
            Field(tipAmount,"Tip amount",Modifier.weight(1f),enabled=tipIsAmount){tipAmount=it}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Field(tax,"Tax %",Modifier.weight(1f)){tax=it}
            Field(people,"Number of people",Modifier.weight(1f)){people=it}
        }
        if(!tipIsAmount)Choices(listOf("0","5","10","15","18","20","25"),percent,{percent=it})
        result.getOrNull()?.let {r->
            val implied=runCatching{
                if(tipIsAmount)Money.impliedTipPercent(decimalInput(bill),r.tip)?.setScale(4,RoundingMode.HALF_UP)?.stripTrailingZeros()
                else decimalInput(percent).stripTrailingZeros()
            }.getOrNull()
            Text("Total  ${Money.format(r.total)}",style=MaterialTheme.typography.headlineMedium)
            Text("Tip  ${Money.format(r.tip)}${if(implied!=null)"   ·   ${Money.format(implied)}%" else ""}   ·   Tax  ${Money.format(r.tax)}")
            HorizontalDivider()
            Text("Per person  ${Money.format(r.share)}",style=MaterialTheme.typography.titleLarge)
            if(r.extraPeople>0)Text("${r.extraPeople} ${if(r.extraPeople==1)"person pays" else "people pay"} ${Money.format(r.extraShare)}; the others pay ${Money.format(r.share)}. This keeps the split equal to the total.",fontSize=12.sp)
        } ?: Text("Enter valid non-negative amounts and a whole number of people.",color=LocalInstrument.current.danger)
    }
}

@Composable fun CurrencyScreen(m:CalculatorModel) {
    var amount by rememberSaveable{mutableStateOf("100")}
    var from by rememberSaveable{mutableStateOf("USD")}
    var to by rememberSaveable{mutableStateOf("KRW")}
    var manual by rememberSaveable{mutableStateOf(false)}
    var manualRate by rememberSaveable{mutableStateOf("")}
    var dropDecimals by rememberSaveable{mutableStateOf(false)}
    val quickCurrencies=listOf("KRW","USD","EUR","JPY","CNY","GBP","CAD","AUD","CHF","TWD","HKD","SGD","NZD","THB","VND","INR","IDR","MYR","PHP","SEK","NOK","MXN","TRY")
    val uri=LocalUriHandler.current
    val table=m.exchangeRates
    LaunchedEffect(manual){if(!manual)m.loadExchangeRates()}
    LaunchedEffect(manual,table?.fetchedMillis){
        if(!manual&&table!=null){delay((table.fetchedMillis+RateTable.TTL-System.currentTimeMillis()).coerceAtLeast(60000));m.loadExchangeRates()}
    }
    val rate=runCatching{if(manual)decimalInput(manualRate).also{require(it.signum()>0)} else table?.rate(from,to,m.precision.coerceAtLeast(16)) ?: error("No cached rates")}
    val converted=runCatching{decimalInput(amount).multiply(rate.getOrThrow(),MathContext(m.precision.coerceAtLeast(16),RoundingMode.HALF_EVEN))}
    Panel("Currency converter","") {
        Choices(listOf("Online / cache","Manual"),if(manual)"Manual" else "Online / cache",{manual=it=="Manual"})
        Field(amount,"Amount",Modifier.fillMaxWidth()){amount=it}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Column(Modifier.weight(1f)){
                Field(from,"From (ISO code)",Modifier.fillMaxWidth()){from=it.uppercase().take(4);manualRate=""}
                currencyName(from)?.let{Text(it,fontSize=12.sp,color=LocalInstrument.current.muted)}
            }
            Column(Modifier.weight(1f)){
                Field(to,"To (ISO code)",Modifier.fillMaxWidth()){to=it.uppercase().take(4);manualRate=""}
                currencyName(to)?.let{Text(it,fontSize=12.sp,color=LocalInstrument.current.muted)}
            }
        }
        Choices(quickCurrencies,from,{from=it;manualRate=""})
        Choices(quickCurrencies,to,{to=it;manualRate=""})
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            SmallAction("⇄ Swap currencies",fontSize=15.sp) {
                val previous=from;from=to;to=previous
                if(manual)manualRate=runCatching{Money.format(BigDecimal.ONE.divide(decimalInput(manualRate),MathContext.DECIMAL128))}.getOrDefault("")
            }
            FilterChip(selected=dropDecimals,onClick={dropDecimals=!dropDecimals},label={Text("Drop decimals",fontSize=13.sp)})
        }
        converted.getOrNull()?.let {value->
            val shown=if(dropDecimals)value.setScale(0,RoundingMode.DOWN) else displayAmount(value.round(MathContext(m.precision,RoundingMode.HALF_EVEN)))
            Text("≈ ${Money.format(shown)} $to",style=MaterialTheme.typography.headlineMedium)
            Text("1 $from = ${Money.format(displayAmount(rate.getOrThrow().round(MathContext(minOf(m.precision,12)))))} $to",fontSize=13.sp)
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
            if(table==null||table.due(System.currentTimeMillis()))TextButton(onClick={m.loadExchangeRates()},enabled=!m.exchangeBusy){Text("Update rates")}
        }
        Text("Rates By Exchange Rate API",Modifier.clickable{uri.openUri(ExchangeRepository.SOURCE)},color=LocalInstrument.current.accent,fontSize=12.sp)
    }
}
