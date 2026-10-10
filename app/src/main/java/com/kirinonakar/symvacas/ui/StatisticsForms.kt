package com.kirinonakar.symvacas.ui

import org.json.JSONObject

/** Map engine predictor positions to the labels of the selected data columns. */
internal fun advancedStatisticsTermLabels(definition:JSONObject,rows:List<List<String>>,settings:JSONObject,columnLabels:List<String>):Map<String,String> {
    val n=rows.maxOfOrNull {it.size} ?: 0;val id=definition.getString("id")
    val controls=definition.optJSONArray("controls")
    val opts=(0 until (controls?.length() ?: 0)).associate {index->val field=controls!!.getJSONObject(index);val key=field.getString("key");key to settings.optString(key,field.get("default").toString())}
    fun column(key:String)=opts[key]?.toIntOrNull()?.let {if(it==-1)n-1 else it} ?: -1
    if(id in listOf("twowayanova","linearmodel"))return statisticsFactorialData(rows,opts,columnLabels).labels
    if(id=="kstest"&&opts["mode"]!="two")return mapOf("sample:1" to columnLabels.getOrElse(column("first")){"Sample 1"})
    if(id in listOf("cohend","bayescompare","levene","bartlett","eta2","friedman","repeatedanova","kstest")||id=="bayesbootstrap"&&opts["layout"]!="single") {
        val selections=opts+mapOf("grouping" to (if(id=="bayesbootstrap")opts["layout"].orEmpty() else opts["grouping"].orEmpty()))
        val plan=statisticsComparisonData(rows,selections,all=id in listOf("levene","bartlett","eta2","friedman","repeatedanova"))
        var names=plan.labels.map {at->if(selections["grouping"]=="groups")at else columnLabels.getOrElse(at.toInt()){"Sample ${at.toInt()+1}"}}
        if(id=="bayesbootstrap"&&opts["order"]=="reverse"&&opts["firstGroup"].isNullOrBlank()&&opts["secondGroup"].isNullOrBlank())names=names.reversed()
        val labels=names.mapIndexed {index,name->"sample:${index+1}" to name}.toMap().toMutableMap()
        if(id in listOf("friedman","repeatedanova"))names.forEachIndexed {index,name->labels["feature:${index+1}"]=name}
        if(id=="bayesbootstrap"){labels["sample:A"]=names[0];labels["sample:B"]=names[1]};return labels
    }
    if(columnLabels.isEmpty())return emptyMap()
    fun multiple(excluded:List<Int>)=if(opts["predictors"]=="auto")(0 until n).filter {it !in excluded} else opts["predictors"].orEmpty().split(',').filter(String::isNotBlank).map(String::toInt)
    val predictors=when(id) {
        "pca","kmeans","friedman"->{
            val columns=if(opts["columns"]=="auto")(0 until n).toList() else opts["columns"].orEmpty().split(',').filter(String::isNotBlank).map(String::toInt)
            return columns.mapIndexed {index,at->"feature:${index+1}" to columnLabels.getOrElse(at){"Feature ${index+1}"}}.toMap()
        }
        "mcnemar"->{
            if(opts["layout"]=="groups"){val plan=statisticsComparisonData(rows,opts+mapOf("grouping" to "groups"),paired=true);return statisticsCategoryLabels(plan.matrix.map {it[0] to it[1]},plan.labels[0],plan.labels[1],shared=true)}
            val first=column("first");val second=column("second")
            val pairs=if(opts["layout"]=="pairs")statisticsCategoryPairs(rows,first,second) else emptyList()
            return statisticsCategoryLabels(pairs,columnLabels.getOrElse(first){"x"},columnLabels.getOrElse(second){"y"},shared=true)
        }
        "glm"->multiple(listOf(column("response"))+if(opts["adjustment"]!="none")listOf(column("offset")) else emptyList())
        "ancova"->{
            val labels=mutableMapOf("Group" to columnLabels.getOrElse(column("group")){"Group"})
            rows.map {it.getOrElse(column("group")){""}.trim()}.distinct().forEachIndexed {index,group->labels["group:${index+1}"]=group}
            multiple(listOf(column("group"),column("response"))).forEachIndexed {index,at->labels["x${index+1}"]=columnLabels.getOrElse(at){"x${index+1}"}}
            return labels
        }
        "mixedmodel","gee","glmm"->multiple(listOf(column("subject"),column("response"))+if(id=="glmm"&&opts["family"]!="binomial"&&opts["adjustment"]!="none")listOf(column("offset")) else emptyList())
        "cox"->multiple(listOf(column("time"),column("event"))+if(opts["truncation"]=="entry")listOf(column("entry")) else emptyList())
        "poissonreg","nbreg"->multiple(listOf(column("response"))+if(opts["adjustment"]!="none")listOf(column("offset")) else emptyList())
        "ordinal","multinomial","crossvalidate"->multiple(listOf(column("response")))
        "survivalanalysis"->{
            val plan=survivalAnalysisPlan(rows,settings,columnLabels);val labels=mutableMapOf<String,String>()
            plan.groups.drop(1).forEachIndexed {index,group->
                val label="${columnLabels[column("group")]}: $group / ${plan.groups.first()}"
                labels["group:${index+1}"]=label;labels["x${index+1}"]=label
            }
            plan.predictors.forEachIndexed {index,name->labels["predictor:$index"]=name;labels["x${(plan.groups.size-1).coerceAtLeast(0)+index+1}"]=name}
            return labels
        }
        else->emptyList()
    }
    val labels=predictors.mapIndexed {index,column->"x${index+1}" to (columnLabels.getOrNull(column) ?: "x${index+1}")}.toMap().toMutableMap()
    if(id=="gee")for((i,j) in interactionPairs(opts["interactions"].orEmpty(),predictors,columnLabels)) {
        labels[if(i==j)"x$i^2" else "x$i:x$j"]=if(i==j)"${labels["x$i"]}^2" else "${labels["x$i"]}:${labels["x$j"]}"
    }
    return labels
}

/** Construct the same selected-column analysis plan used by the Web form. */
internal fun guidedStatisticsCommand(definition:JSONObject,rows:List<List<String>>,settings:JSONObject=JSONObject(),columnLabels:List<String> = emptyList(),removeComputationLimit:Boolean=false):String {
    if(definition.getString("id")=="survivalanalysis")return survivalAnalysisPlan(rows,settings,columnLabels).command
    if(!definition.has("controls"))return advancedStatisticsCommand(definition,rows)
    require(definition.getString("input")=="none"||rows.any {row->row.any(String::isNotBlank)}) {"Enter data first"}
    val n=rows.maxOfOrNull {it.size} ?: 0;val id=definition.getString("id");val controls=definition.getJSONArray("controls")
    val opts=(0 until controls.length()).associate {index->val field=controls.getJSONObject(index);val key=field.getString("key");key to settings.optString(key,field.get("default").toString())}
    for(i in 0 until controls.length()) {
        val field=controls.getJSONObject(i)
        if(field.getString("type")=="choice") {
            val choices=field.getJSONArray("choices")
            require((0 until choices.length()).any {index->
                val choice=choices.getJSONObject(index);val conditions=choice.optJSONObject("when")
                choice.getString("id")==opts[field.getString("key")]&&
                    (conditions==null||conditions.keys().asSequence().all {key->
                        val values=conditions.getJSONArray(key)
                        (0 until values.length()).any {values.getString(it)==opts[key]}
                    })
            }) {"Invalid analysis option"}
        }
    }
    fun col(key:String):Int {val raw=opts.getValue(key).toIntOrNull();val index=if(raw==-1)n-1 else raw;require(index!=null&&index in 0 until n) {"Choose valid data columns"};return index}
    fun vector(values:List<String>)=values.joinToString(",","[","]")
    fun table(values:List<List<String>>)=values.joinToString(",","[","]",transform=::vector)
    fun values(index:Int)=rows.map {it.getOrElse(index){""}.trim()}.filter(String::isNotBlank)
    fun multiple(key:String,excluded:List<Int> = emptyList()):List<Int> {
        val raw=opts.getValue(key);val indices=if(raw=="auto")(0 until n).filter {it !in excluded} else raw.split(',').filter(String::isNotBlank).map {it.toIntOrNull() ?: -1}
        require(indices.isNotEmpty()&&indices.distinct().size==indices.size&&indices.all {it in 0 until n&&it !in excluded}) {"Choose distinct analysis columns"}
        return indices
    }
    fun distinct(indices:List<Int>) {require(indices.distinct().size==indices.size) {"Roles must use different columns"}}
    fun complete(indices:List<Int>):List<List<String>> {distinct(indices);val selected=rows.map {row->indices.map {row.getOrElse(it){""}.trim()}};require(selected.all {row->row.all(String::isNotBlank)}) {"Complete selected rows required"};return selected}
    fun eventRows(indices:List<Int>):List<List<String>> {
        val selected=complete(listOf(col("time"),col("event"))+indices);val eventValue=opts.getValue("eventValue")
        require(eventValue.isNotBlank()) {"Enter the event value"}
        val states=selected.map {it[1]}.distinct();require(states.size<=2) {"Event column must have at most two values"}
        require(states.size==1||eventValue in states) {"Event value does not occur in the selected column"}
        return selected.map {row->listOf(row[0],if(row[1]==eventValue)"1" else "0")+row.drop(2)}
    }
    return when(id) {
        "testpower","samplesize"->{
            val keys=listOf("effect",if(id=="testpower")"n" else "power","alpha")
            require(keys.all {opts[it].orEmpty().isNotBlank()}) {"Enter all study design parameters"}
            "$id(${keys.joinToString(","){opts.getValue(it)}},${opts["design"]}${if(opts["tail"]=="two")"" else ","+opts["tail"]})"
        }
        "bootstrapci"->"bootstrapci(${vector(values(col("column")))},${opts["statistic"]},${opts["level"]},${opts["samples"]},${opts["seed"]})"
        "cohend"->{val plan=statisticsComparisonData(rows,opts,paired=opts["design"]=="paired",strict=true);"cohend(${plan.samples.joinToString(",",transform=::vector)},${opts["design"]})"}
        "twowayanova","linearmodel"->{val plan=statisticsFactorialData(rows,opts,columnLabels);if(id=="twowayanova")"twowayanova(${table(plan.encoded)},${opts["interaction"]})" else "linearmodel(${table(plan.encoded)},${vector(plan.categorical)},${opts["order"]},${opts["ssType"]},${opts["coding"]})"}
        "multinomial","ordinal"->{val response=col("response");"$id(${table(complete(multiple("predictors",listOf(response))+response))})"}
        "kmeans"->"kmeans(${table(complete(multiple("columns")))},${opts["clusters"]},${opts["seed"]})"
        "friedman"->{val plan=statisticsComparisonData(rows,opts,all=true,paired=true,strict=true);require(plan.samples.size>=3) {"Choose at least three conditions"};"friedman(${table(plan.matrix)})"}
        "bayesbootstrap"->{
            require(listOf("level","samples","seed").all {opts[it].orEmpty().isNotBlank()}) {"Enter all interval and simulation parameters"}
            val suffix="${opts["statistic"]},${opts["level"]},${opts["samples"]},${opts["seed"]}"
            if(opts["layout"]=="single") {
                val data=values(col("column"));require(data.size>=2) {"Enter at least two observations"}
                "bayesbootstrap(${vector(data)},$suffix)"
            } else {
                val method=opts["comparison"]
                val plan=statisticsComparisonData(rows,opts+mapOf("grouping" to opts.getValue("layout")),paired=method=="paired",strict=true)
                val samples=if(opts["order"]=="reverse"&&opts["firstGroup"].isNullOrBlank()&&opts["secondGroup"].isNullOrBlank())plan.samples.reversed() else plan.samples
                require(samples.all {it.size>=2}) {"Enter at least two observations in each group"}
                "bayesbootstrap(${samples.joinToString(",",transform=::vector)},$suffix,$method)"
            }
        }
        "pca"->{
            val columns=multiple("columns");val count=opts["components"]?.toIntOrNull()
            require(count!=null&&count in 1..columns.size) {"Components must be between 1 and the selected feature count"}
            "pca(${table(complete(columns))},$count,${opts["standardize"]})"
        }
        "padjust"->"padjust(${vector(values(col("column")))},${opts["method"]},${opts["alpha"]})"
        "ancova"->{
            val group=col("group");val response=col("response");distinct(listOf(group,response))
            val selected=complete(listOf(group)+multiple("predictors",listOf(group,response))+response)
            val labels=selected.map {it[0]}.distinct();require(labels.size>=2) {"Choose at least two groups"}
            val mapped=selected.map {row->listOf((labels.indexOf(row[0])+1).toString())+row.drop(1)}
            "ancova(${table(mapped)},${opts["level"]},${if(opts["slopes"]=="test")1 else 0})"
        }
        "glm"->{
            val response=col("response");val offset=if(opts["adjustment"]!="none")col("offset") else null
            val reserved=listOfNotNull(response,offset);distinct(reserved)
            val mapped=complete(multiple("predictors",reserved)+response)
            val suffix=if(offset==null)"" else ",${vector(complete(listOf(offset)).map {it[0]})},${opts["adjustment"]}"
            val alpha=if(opts["family"]=="nbinom") {if(opts["dispersionMode"]=="estimate")"estimate" else opts["alpha"]} else "1"
            "glm(${table(mapped)},${opts["family"]},${opts["link"]},$alpha$suffix)"
        }
        "bayescompare"->{
            val samples=statisticsComparisonData(rows,opts).samples
            require(samples.all {it.size>=2}) {"Enter at least two observations in each group"}
            val keys=listOf("variance","mu","kappa","alpha","beta","level","samples","seed")
            require(keys.all {opts.getValue(it).isNotBlank()}) {"Enter all Bayesian prior and interval parameters"}
            "$id(${samples.joinToString(",",transform=::vector)},${keys.joinToString(",") {opts.getValue(it).trim()}})"
        }
        "bayesproportion","bayesmean","bayesrate"->{
            val data=when {
                id=="bayesproportion"&&opts["layout"]=="counts"->table(complete(listOf(col("successes"),col("trials"))))
                id=="bayesrate"&&opts["layout"]=="exposure"->table(complete(listOf(col("column"),col("exposure"))))
                else->vector(complete(listOf(col("column"))).map {it[0]})
            }
            val keys=if(id=="bayesmean")listOf("mu","kappa","alpha","beta","level","threshold") else listOf("alpha","beta","level","threshold")
            require(keys.all {opts.getValue(it).isNotBlank()}) {"Enter all Bayesian prior and interval parameters"}
            "$id($data,${keys.joinToString(",") {opts.getValue(it).trim()}})"
        }
        "levene","bartlett","eta2"->{
            val samples=if(opts["grouping"]=="groups") {val pairs=complete(listOf(col("group"),col("value")));pairs.map {it[0]}.distinct().map {label->pairs.filter {it[0]==label}.map {it[1]}}} else multiple("columns").map(::values)
            require(samples.size>=2) {"Choose at least two groups"};"$id(${samples.joinToString(",",transform=::vector)})"
        }
        "mcnemar"->{
            val first=col("first");val second=col("second");distinct(listOf(first,second))
            val pairs=if(opts["layout"]=="groups")statisticsComparisonData(rows,opts+mapOf("grouping" to "groups"),paired=true).matrix else if(opts["layout"]=="pairs")statisticsCategoryPairs(rows,first,second).map {listOf(it.first,it.second)} else complete(listOf(first,second))
            val counts=if(opts["layout"]!="counts") {
                val labels=pairs.flatten().distinct();require(labels.size==2) {"Paired observations require the same two categories"}
                val bins=Array(2){IntArray(2)};pairs.forEach {bins[labels.indexOf(it[0])][labels.indexOf(it[1])]++};bins.map {row->row.map(Int::toString)}
            } else pairs.also {require(it.size==2) {"The count table needs exactly two rows"}}
            "mcnemar(${table(counts)},${opts["method"]})"
        }
        "kaplanmeier"->"kaplanmeier(${table(eventRows(emptyList()))},${opts["level"]})"
        "logrank"->{
            val selected=eventRows(listOf(col("group")));val labels=selected.map {it[2]}.distinct()
            require(labels.size==2) {"Log-rank requires exactly two groups"}
            "logrank(${labels.joinToString(",") {label->table(selected.filter {it[2]==label}.map {it.take(2)})}})"
        }
        "cox"->{
            val time=col("time");val event=col("event");val entry=if(opts["truncation"]=="entry")col("entry") else null
            val reserved=listOfNotNull(time,event,entry)
            val selected=eventRows(listOfNotNull(entry)+multiple("predictors",reserved))
            "cox(${table(selected)},${opts["ties"]},${if(entry==null)-1 else 2},${if(opts["ph"]=="test")1 else 0})"
        }
        "repeatedanova"->{val plan=statisticsComparisonData(rows,opts,all=true,paired=true,strict=true);require(plan.samples.size>=2) {"Choose at least two conditions"};"repeatedanova(${table(plan.matrix)},${opts["factor2"]})"}
        "poissonreg","nbreg"->{
            val response=col("response");val offset=if(opts["adjustment"]!="none")col("offset") else null
            val reserved=listOfNotNull(response,offset);distinct(reserved)
            val predictors=multiple("predictors",reserved);val mapped=complete(predictors+response)
            val suffix=if(offset==null)"" else ",${vector(complete(listOf(offset)).map {it[0]})},${opts["adjustment"]}"
            "$id(${table(mapped)}$suffix)"
        }
        "mixedmodel","gee","glmm"->{
            val subject=col("subject");val response=col("response");distinct(listOf(subject,response))
            val offset=if(id=="glmm"&&opts["family"]!="binomial"&&opts["adjustment"]!="none")col("offset") else null
            val reserved=listOfNotNull(subject,response,offset);distinct(reserved)
            val selectedColumns=multiple("predictors",reserved)
            val selected=complete(listOf(subject)+selectedColumns+response);val labels=selected.map {it[0]}.distinct()
            val mapped=selected.map {row->listOf((labels.indexOf(row[0])+1).toString())+row.drop(1)}
            if(id=="glmm") {
                val slope=(opts["slope"] ?: "0").trim().toIntOrNull()
                require(slope!=null&&slope in 0..selectedColumns.size) {"Choose 0 or one selected predictor position for the GLMM random slope"}
                if(slope>0) {
                    val suffix=if(offset==null)",[],offset" else ",${vector(complete(listOf(offset)).map {it[0]})},${opts["adjustment"]}"
                    "glmm(${table(mapped)},${opts["family"]},1$suffix,likelihood,$slope)"
                } else {
                    var suffix=if(offset==null)"" else ",${vector(complete(listOf(offset)).map {it[0]})},${opts["adjustment"]}"
                    if(opts["sensitivity"]=="refit")suffix=(suffix.ifEmpty {",[],offset"})+",refit"
                    "glmm(${table(mapped)},${opts["family"]},${opts["points"]}$suffix)"
                }
            } else if(id=="gee") {
                val pairs=interactionPairs(opts["interactions"],selectedColumns,columnLabels)
                require(pairs.distinct().size==pairs.size) {"Interaction pairs must be distinct"}
                val correction=opts["correction"]=="small"
                val suffix=(if(pairs.isEmpty()&&!correction)"" else ","+pairs.joinToString(",","[","]") {pair->"[${pair.first},${pair.second}]"})+(if(correction)",small" else "")
                "gee(${table(mapped)},${opts["family"]},${opts["corr"]}$suffix)"
            } else {
                val positions=(opts["slope"] ?: "0").split(',').map(String::trim).filter(String::isNotBlank)
                require(positions.all {it.toIntOrNull()!=null}) {"Enter random-slope positions like 0 or 1,2"}
                val numbers=positions.mapNotNull {it.toIntOrNull()}.filter {it!=0}
                require(numbers.all {it in 1..19}&&numbers.distinct().size==numbers.size) {"Random-slope positions must be distinct predictor numbers"}
                val argument=when(numbers.size) {0->"0";1->numbers[0].toString();else->"["+numbers.joinToString(",")+"]"}
                val ci=when(opts["ci"]) {"profile"->",profile";"bootstrap"->",[bootstrap,${opts["ciSamples"] ?: "200"},${opts["ciSeed"] ?: "0"}]";else->""}
                "mixedmodel(${table(mapped)},$argument,${opts["method"]}$ci)"
            }
        }
        "kstest"->{
            val first=col("first")
            if(opts["mode"]=="two") "kstest(${statisticsComparisonData(rows,opts).samples.joinToString(",",transform=::vector)})"
            else "kstest(${vector(values(first))},${opts["mode"]},${opts["location"]},${opts["scale"]})"
        }
        "impute"->{
            val width=rows.maxOf {it.size}
            val cells=rows.map {row->List(width){index->row.getOrElse(index){""}.trim().ifBlank{"NA"}}}
            val method=opts.getValue("method")
            require(method in listOf("mean","median","mode","regression","knn")) {"Invalid imputation method"}
            val neighbors=if(method=="knn") {val k=opts.getValue("k").trim();require(k.toIntOrNull()?.let {it>=1&&(removeComputationLimit||it<=100)}==true) {"Enter 1-100 neighbours"};",$k"} else ""
            "impute(${table(cells)},$method$neighbors)"
        }
        "crossvalidate"->{
            val response=col("response")
            val cells=complete(multiple("predictors",listOf(response))+response)
            val folds=opts.getValue("folds").trim();require(folds.toIntOrNull()?.let {it>=2&&it<=rows.size}==true) {"Folds must be between 2 and the row count"}
            val seed=opts.getValue("seed").trim();require(seed.toLongOrNull()?.let {it>=0}==true) {"Seed must be a nonnegative integer"}
            val model=opts.getValue("model");val split=opts.getValue("split")
            require(model in listOf("linear","ridge","lasso","elasticnet","logistic")) {"Invalid cross-validation model"}
            require(split in listOf("random","blocked","stratified")) {"Invalid cross-validation split"}
            val penalty=when(model) {
                "elasticnet"->",["+opts.getValue("alpha").trim()+","+opts.getValue("ratio").trim()+"]"
                "linear"->""
                else->","+opts.getValue("alpha").trim()
            }
            val suffix=if(split=="random"&&model=="linear")"" else ",$split,$model$penalty"
            "crossvalidate(${table(cells)},$folds,$seed$suffix)"
        }
        else->error("Unknown analysis form")
    }
}

/** Parse "age,weight", "age (y)", "y,z", "2,3" (column numbers) or "p1,p2" (predictor order) interaction pairs. */
internal fun interactionPairs(value:String?,predictors:List<Int> = emptyList(),columnLabels:List<String> = emptyList()):List<Pair<Int,Int>> {
    val text=value.orEmpty().trim()
    if(text.isEmpty())return emptyList()
    val names=columnLabels.map {it.trim().lowercase()}
    fun aliasColumn(alias:String):Int? = when(alias) {
        "x"->0
        "y"->1
        "z"->2
        else->Regex("^x(\\d+)\$").find(alias)?.groupValues?.get(1)?.toInt()?.minus(1)
    }
    fun splitLabel(label:String):Pair<String,Int?> {
        val found=Regex("^(.+?)\\s*\\(\\s*([^()]*?)\\s*\\)\$").find(label)
        if(found==null)return label to null
        return found.groupValues[1].trim() to aliasColumn(found.groupValues[2].trim())
    }
    val headers=names.map {label->val parts=splitLabel(label);if(parts.second==null)label else parts.first}
    fun positionOf(column:Int):Int {
        val position=predictors.indexOf(column)
        require(position>=0) {"Interaction columns must be among the selected predictors"}
        return position+1
    }
    fun resolve(token:String):Int {
        val raw=token.trim();val lower=raw.lowercase()
        require(raw.isNotEmpty()) {"Use interaction pairs like age,weight;2,3"}
        val labelled=splitLabel(lower);val column=labelled.second
        if(column!=null) {
            if(headers.getOrNull(column)==labelled.first||names.getOrNull(column)==labelled.first||names.getOrNull(column)==lower)return positionOf(column)
            val byHeader=headers.indexOf(labelled.first)
            if(byHeader>=0)return positionOf(byHeader)
            val byName=names.indexOf(lower)
            require(byName>=0) {"Unknown interaction column"}
            return positionOf(byName)
        }
        // Shown column letters (x, y, z, x4, x5, ...) address the Nth data column.
        val alias=aliasColumn(lower)
        if(alias!=null&&alias>=0) {
            if(alias in predictors)return positionOf(alias)
            val byName=names.indexOf(lower)
            if(byName>=0)return positionOf(byName)
            val byHeader=headers.indexOf(lower)
            if(byHeader>=0)return positionOf(byHeader)
            throw IllegalArgumentException("Interaction columns must be among the selected predictors")
        }
        val name=names.indexOf(lower)
        if(name>=0)return positionOf(name)
        val header=headers.indexOf(lower)
        if(header>=0)return positionOf(header)
        val named=Regex("^p(\\d+)\$").find(lower)
        if(named!=null) {
            val at=named.groupValues[1].toInt()
            require(at in 1..predictors.size) {"Interaction positions must be within the selected predictors"}
            return at
        }
        val spelled=Regex("^column\\s*(\\d+)\$").find(lower)?.groupValues?.get(1)
        val digits=raw.removePrefix("#").takeIf {it.isNotEmpty()&&it.all(Char::isDigit)}
        val number=(spelled?:digits)?.toInt() ?: -1
        require(number>=1) {"Unknown interaction column"}
        return positionOf(number-1)
    }
    return text.split(';').map {group->
        val tokens=group.split(',','+').filter(String::isNotBlank)
        require(tokens.size==2) {"Use interaction pairs like age,weight;2,3"}
        val first=resolve(tokens[0]);val second=resolve(tokens[1])
        if(first<=second)first to second else second to first
    }
}

internal data class SurvivalPlan(val command:String,val groups:List<String>,val predictors:List<String>)

internal fun survivalAnalysisPlan(rows:List<List<String>>,settings:JSONObject=JSONObject(),labels:List<String> = emptyList()):SurvivalPlan {
    require(rows.isNotEmpty()) {"Enter data first"}
    val n=rows.maxOf {it.size}
    fun option(key:String,default:String)=settings.optString(key,default)
    fun col(key:String,default:String):Int {val i=option(key,default).toIntOrNull();require(i!=null&&i in 0 until n) {"Choose valid data columns"};return i}
    val grouping=option("grouping","groups");val cox=option("cox","0")
    require(grouping in listOf("groups","all")&&cox in listOf("0","1")) {"Invalid analysis option"}
    val time=col("time","0");val event=col("event","1");val group=if(grouping=="groups")col("group","2") else null
    val reserved=listOfNotNull(time,event,group)
    require(reserved.distinct().size==reserved.size) {"Roles must use different columns"}
    val predictors=if(cox=="1")option("predictors","").split(',').filter(String::isNotBlank).map {it.toIntOrNull() ?: -1} else emptyList()
    require(predictors.distinct().size==predictors.size&&predictors.all {it in 0 until n&&it !in reserved}) {"Choose distinct analysis columns"}
    val selected=rows.map {row->(reserved+predictors).map {row.getOrElse(it){""}.trim()}}
    require(selected.all {row->row.all(String::isNotBlank)}) {"Complete selected rows required"}
    val eventValue=option("eventValue","1").trim();require(eventValue.isNotBlank()) {"Enter the event value"}
    val states=selected.map {it[1]}.distinct();require(states.size<=2) {"Event column must have at most two values"}
    require(states.size==1||eventValue in states) {"Event value does not occur in the selected column"}
    val groups=if(group==null)emptyList() else selected.map {it[2]}.distinct()
    val encoded=selected.map {row->listOf(row[0],if(row[1]==eventValue)"1" else "0",if(group==null)"1" else (groups.indexOf(row[2])+1).toString())+row.drop(reserved.size)}
    val table=encoded.joinToString(",","[","]") {it.joinToString(",","[","]")}
    val ties=option("ties","efron");val ph=if(option("ph","test")=="test")1 else 0
    return SurvivalPlan("survivalanalysis($table,$cox,$ties,-1,$ph)",groups,predictors.map {labels.getOrNull(it) ?: listOf("x","y","z").getOrNull(it) ?: "x${it+1}"})
}
