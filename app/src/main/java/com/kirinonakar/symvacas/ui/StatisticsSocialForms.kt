package com.kirinonakar.symvacas.ui

internal val socialAnalysisIds=setOf("cronbach","efa","cfa","sem","manova","mediation","moderation","cramerv","phi","cohenkappa","dunn","discriminantanalysis","quantreg","zeroinflated","tobit","hcluster")

internal data class SocialStatisticsPlan(val expression:String,val labels:Map<String,String>)

/** Selected roles and category encoding shared with statistics-social-forms.js. */
internal fun socialStatisticsPlan(id:String,rows:List<List<String>>,opts:Map<String,String>,columnLabels:List<String>):SocialStatisticsPlan {
    val n=rows.maxOfOrNull {it.size} ?: 0;val labels=mutableMapOf<String,String>()
    fun col(key:String):Int {val raw=opts.getValue(key).toIntOrNull();val at=if(raw==-1)n-1 else raw;require(at!=null&&at in 0 until n){"Choose valid data columns"};return at}
    fun columns(key:String,excluded:List<Int> = emptyList(),optional:Boolean=false):List<Int> {
        val raw=opts.getValue(key);val indices=if(raw=="auto")(0 until n).filter {it !in excluded} else raw.split(',').filter(String::isNotBlank).map {it.toIntOrNull() ?: -1}
        require((optional||indices.isNotEmpty())&&indices.distinct().size==indices.size&&indices.all {it in 0 until n&&it !in excluded}){"Choose distinct analysis columns"};return indices
    }
    fun complete(indices:List<Int>):List<List<String>> {
        require(indices.distinct().size==indices.size){"Roles must use different columns"}
        return rows.map {row->indices.map {row.getOrElse(it){""}.trim()}}.also {selected->require(selected.all {row->row.all(String::isNotBlank)}){"Complete selected rows required"}}
    }
    fun vector(values:List<String>)=values.joinToString(",","[","]")
    fun table(values:List<List<String>>)=values.joinToString(",","[","]",transform=::vector)
    fun label(at:Int,fallback:String)=columnLabels.getOrElse(at){fallback}
    val expression=when(id) {
        "dunn"->{
            val plan=statisticsComparisonData(rows,opts,all=true);require(plan.samples.size>=2){"Choose at least two groups"}
            plan.labels.forEachIndexed {i,at->labels["sample:${i+1}"]=if(opts["grouping"]=="groups")at else label(at.toInt(),"Sample ${i+1}")}
            "dunn(${table(plan.samples)},${opts["adjustment"]})"
        }
        "cronbach","efa","cfa","sem","hcluster"->{
            val multigroup=id in listOf("cfa","sem")&&opts["groupMode"]=="multi"
            val group=if(multigroup)col("group") else -1
            val indices=columns("columns",if(multigroup)listOf(group) else emptyList())
            val selected=if(id in listOf("cfa","sem")&&opts["missing"]=="fiml")rows.map {row->indices.map {row.getOrElse(it){""}.trim().ifBlank {"NA"}}} else complete(indices)
            indices.forEachIndexed {i,at->labels["feature:${i+1}"]=label(at,"Feature ${i+1}")}
            val samples=if(opts["factors"]=="parallel"&&(opts["parallelSamples"]?.toDoubleOrNull() ?: 0.0)==0.0)"100" else opts["parallelSamples"] ?: "0"
            val efaExtra=if(opts["extraction"]=="pca"||(samples.toDoubleOrNull() ?: 0.0)>0.0||opts["factors"]=="parallel")",${opts["extraction"] ?: "pa"},$samples,${opts["seed"] ?: "0"},${opts["percentile"] ?: "0.95"}" else ""
            val suffix=when(id){"cronbach"->opts["mode"];"efa"->"${opts["factors"]},${opts["rotation"]}$efaExtra";"hcluster"->"${opts["clusters"]},${opts["linkage"]},${opts["standardize"]}";else->null}
            if(suffix!=null)"$id(${table(selected)},$suffix)" else {
                val factors=opts.getValue("factors").split(',').map(String::trim)
                require(factors.size==indices.size&&factors.all {it.matches(Regex("\\d+"))&&(it.toIntOrNull() ?: 0)>0}){"Specify one positive factor ID per selected indicator"}
                val paths=opts["paths"].orEmpty().trim().split(';').filter(String::isNotBlank).map {it.split(',').map(String::trim)}
                require(paths.all {pair->pair.size==2&&pair.all {it.matches(Regex("\\d+"))}}){"Use latent paths like 1,2;2,3"}
                val cross=opts["cross"].orEmpty().trim().split(';').filter(String::isNotBlank).map {it.split(',').map(String::trim)}
                require(cross.all {pair->pair.size==2&&pair.all {it.matches(Regex("\\d+"))&&(it.toIntOrNull() ?: 0)>0}}){"Use cross-loadings like 2,2;5,1 in selected indicator order"}
                val extra=if(cross.isNotEmpty()||opts["missing"]=="fiml"||multigroup) {
                    val ids=if(multigroup) {
                        val groupRows=complete(listOf(group));val groups=groupRows.map {it[0]}.distinct()
                        groups.forEachIndexed {i,name->labels["group:${i+1}"]=name}
                        groupRows.map {(groups.indexOf(it[0])+1).toString()}
                    } else emptyList()
                    ",${table(cross)},${opts["missing"] ?: "complete"},${vector(ids)},${opts["invariance"] ?: "configural"}"
                } else ""
                "$id(${table(selected)},${vector(factors)}${if(id=="sem")","+table(paths) else ""}$extra)"
            }
        }
        "manova"->{
            if(opts["design"]=="factorial") {
                val factors=columns("factorColumns");val responses=columns("responses",factors);val selected=complete(factors+responses)
                val categories=factors.indices.map {i->selected.map {it[i]}.distinct()}
                factors.forEachIndexed {i,at->labels["factor:${i+1}"]=label(at,"Factor ${i+1}")};responses.forEachIndexed {i,at->labels["response:${i+1}"]=label(at,"Response ${i+1}")}
                val encoded=selected.map {row->row.mapIndexed {i,v->if(i<factors.size)(categories[i].indexOf(v)+1).toString() else v}}
                "manova(${table(encoded)},factorial,${factors.size},${opts["order"] ?: "2"})"
            } else if(opts["design"]=="repeated") {
                "manova(${table(complete(columns("responses")))},repeated,${opts["occasions"] ?: "3"})"
            } else {
            val group=col("group");val responses=columns("responses",listOf(group));val selected=complete(listOf(group)+responses);val groups=selected.map {it[0]}.distinct()
            groups.forEachIndexed {i,name->labels["group:${i+1}"]=name};responses.forEachIndexed {i,at->labels["response:${i+1}"]=label(at,"Response ${i+1}")}
            "manova(${table(selected.map {listOf((groups.indexOf(it[0])+1).toString())+it.drop(1)})})"
            }
        }
        "mediation","moderation"->{
            val x=col("x");val middle=col("middle");val response=col("response");val covariates=columns("covariates",listOf(x,middle,response),true);val indices=listOf(x,middle)+covariates
            indices.forEachIndexed {i,at->labels["x${i+1}"]=label(at,"x${i+1}")};labels["x1:x2"]="${labels["x1"]}:${labels["x2"]}"
            "$id(${table(complete(indices+response))}${if(id=="mediation")",${opts["samples"]},${opts["seed"]}" else ""})"
        }
        "cramerv","phi","cohenkappa"->{
            val counts=if(opts["layout"]=="counts")complete(columns("columns")) else {
                val first=col("first");val second=col("second");val pairs=complete(listOf(first,second))
                var left=pairs.map {it[0]}.distinct();var right=pairs.map {it[1]}.distinct()
                if(id=="cohenkappa") {
                    val categories=opts["categories"].orEmpty().trim()
                    require(opts["weights"]=="unweighted"||categories.isNotEmpty()){"Specify category order for weighted kappa"}
                    left=if(categories.isNotEmpty())categories.split(',').map(String::trim) else pairs.flatten().distinct();right=left
                    require(left.distinct().size==left.size&&left.all(String::isNotBlank)&&pairs.flatten().all {it in left}){"Category order must contain every observed category exactly once"}
                }
                labels["table:row"]=label(first,"First");labels["table:column"]=label(second,"Second")
                left.forEachIndexed {i,name->labels["table:row:${i+1}"]=name};right.forEachIndexed {i,name->labels["table:column:${i+1}"]=name}
                left.map {a->right.map {b->pairs.count {it[0]==a&&it[1]==b}.toString()}}
            }
            "$id(${table(counts)}${if(id=="cohenkappa")","+opts["weights"] else ""})"
        }
        else->{
            val response=col("response");val predictors=columns("predictors",listOf(response));val selected=complete(predictors+response)
            predictors.forEachIndexed {i,at->labels["x${i+1}"]=label(at,"x${i+1}");labels["Count: x${i+1}"]=labels.getValue("x${i+1}");labels["Inflation: x${i+1}"]=labels.getValue("x${i+1}")}
            if(id=="discriminantanalysis") {
                val classes=selected.map {it.last()}.distinct();classes.forEachIndexed {i,name->labels["class:${i+1}"]=name}
                "discriminantanalysis(${table(selected.map {it.dropLast(1)+(classes.indexOf(it.last())+1).toString()})},${opts["method"]},${opts["prior"]})"
            } else {
                val suffix=when(id){"quantreg"->opts["quantile"];"tobit"->"${opts["lower"]},${opts["upper"]}";else->"${opts["family"]},${opts["inflation"]}"}
                "$id(${table(selected)},$suffix)"
            }
        }
    }
    return SocialStatisticsPlan(expression,labels)
}
