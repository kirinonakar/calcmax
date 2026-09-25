package com.example.calcmax.math

/** Source is the serialization; cursor and selection can address whole AST subtrees. */
data class Editor(val source: String = "", val cursor: Int = source.length, val anchor: Int = cursor,
                  val exponent: IntRange? = null, val outside: IntRange? = null, val activeToken: IntRange? = null) {
    fun insert(text: String, inside: Int = text.length): Editor {
        if(cursor==anchor && text in listOf("+","-","−","×","*","·","÷","/","^","∠","=")) {
            val nodes=tree()?.nodes()
            val slot=nodes?.firstOrNull {node->node.kind=="group" && node.args.firstOrNull()?.kind=="hole" && node.start==cursor-1}
            if(slot!=null && nodes.any {node->node.kind=="binary" && node.value=="*" && node.displayOperator=="∘" && node.args.any {it.start==slot.start && it.end==slot.end}}==true)
                return Editor(source.substring(0,slot.start)+text+source.substring(slot.end),slot.start+text.length)
        }
        if(cursor==anchor && exponent==null && text.firstOrNull()?.let{it.isLetterOrDigit()||it=='.'||it=='('||it=='√'}==true &&
            tree()?.nodes()?.any {it.kind=="binary"&&it.value=="^"&&it.end==cursor&&it.args[1].kind !in setOf("hole","group")}==true)
            return Editor(source,cursor).insert("*$text",inside+1)
        if(cursor==anchor && source.getOrNull(cursor-1)==')' && text.firstOrNull()?.let{it.isDigit()||it=='.'}==true) {
            val nodes=tree()?.nodes()
            val completed=nodes?.firstOrNull {node->
                node.kind=="binary" && (node.value=="^" || fraction(node)) &&
                    node.args[1].kind=="group" && node.args[1].end==cursor
            }
            if(completed!=null) {
                val slot=completed.args[1]
                if(slot.args.firstOrNull()?.kind=="hole")return Editor(source,slot.start+1).insert(text,inside)
            }
            val emptyGroup=nodes?.firstOrNull {it.kind=="group" && it.end==cursor && it.args.firstOrNull()?.kind=="hole"}
            if(emptyGroup!=null)return Editor(source,emptyGroup.start+1).insert(text,inside)
            return Editor(source,cursor).insert("*$text",inside+1)
        }
        exponent?.let {region->
            if(cursor in region && anchor in region) {
                val grouped=source.substring(0,region.first)+"("+source.substring(region.first,region.last)+")"+source.substring(region.last)
                return Editor(grouped,cursor+1,anchor+1).insert(text,inside)
            }
        }
        val a = minOf(cursor, anchor).coerceIn(0, source.length)
        val b = maxOf(cursor, anchor).coerceIn(a, source.length)
        val result = source.substring(0,a) + text + source.substring(b)
        val active=activeToken?.let{old->
            val end=old.last+text.length-(b-a)
            Lexer.scan(result).firstOrNull{it.start==old.first&&it.end==end&&it.text.isNotEmpty()}?.let{it.start..it.end}
        }
        return Editor(result, a + inside, activeToken=active)
    }
    private fun hiddenCallOpen(position:Int):Boolean = source.getOrNull(position)=='(' &&
        tree()?.nodes()?.any {it.kind=="call" && it.start<position && it.args.firstOrNull()?.start==position+1}==true
    private fun fraction(node:Expr):Boolean = node.kind=="binary" && node.value=="/" && node.displayOperator!="÷"
    private fun fractionAfterDenominator(position:Int):Expr? = tree()?.nodes()?.filter {node->
        fraction(node) && node.end==position && node.args[1].kind=="group" && node.args[1].end==position
    }?.maxByOrNull {it.end-it.start}
    private fun hiddenFractionDelimiter(position:Int):Boolean = tree()?.nodes()?.any {node->
        fraction(node) && node.args.any {slot->slot.kind=="group" && position in listOf(slot.start,slot.end-1)}
    }==true
    private fun emptyStructuredSlotDelimiter(position:Int):Boolean = tree()?.nodes()?.any {node->
        if(node.kind!="binary" || (node.value !in setOf("^","*") && !fraction(node)))false else {
            node.args.any {slot->slot.kind=="group" && slot.args.firstOrNull()?.kind=="hole" && position in listOf(slot.start,slot.end-1)}
        }
    }==true
    private fun remove(start:Int,end:Int):Editor {
        val nodes=tree()?.nodes()
        val structuralOperand=nodes?.any {node->
            node.kind=="binary" && (node.value=="^" && node.args[0].start==start && node.args[0].end==end ||
                node.value=="*" && node.args.any {it.start==start && it.end==end} ||
                fraction(node) && node.args.any {it.start==start && it.end==end})
        }==true
        val operatorSlot=(cursor!=anchor)&&nodes?.any {node->
            node.kind=="binary" && node.value!="^" && !(node.value=="/" && node.displayOperator!="÷") &&
                node.args.size==2 && node.args[0].end<=start && end<=node.args[1].start && node.args[0].end<node.args[1].start
        }==true
        return copy(cursor=end,anchor=start).insert(if(structuralOperand||operatorSlot)"()" else "",if(structuralOperand||operatorSlot)1 else 0)
    }
    private fun powerAtExponentStart(position:Int):Expr? = if(source.getOrNull(position-1)!='^')null else
        tree()?.nodes()?.filter {it.kind=="binary"&&it.value=="^"&&it.args[1].start==position}
            ?.minByOrNull {it.end-it.start}
    private fun clearPowerBase(power:Expr):Editor {
        val base=power.args[0]
        return if(base.kind=="group"&&base.args.firstOrNull()?.kind=="hole")this else remove(base.start,base.end)
    }
    private fun emptyExponentAt(position:Int):Expr? = tree()?.nodes()?.filter {power->
        if(power.kind!="binary" || power.value!="^")false else {
            val exponent=power.args[1]
            val hole=if(exponent.kind=="group")exponent.args.firstOrNull() else exponent
            hole?.kind=="hole" && hole.start==position
        }
    }?.minByOrNull {it.end-it.start}
    private fun removeTailKeepingHead(head:Expr,end:Int):Editor {
        val inner=head.args.firstOrNull()
        val keep=if(head.kind=="group" && inner?.kind in setOf("number","symbol","call"))inner!! else head
        val retained=source.substring(keep.start,keep.end)
        return Editor(source.substring(0,head.start)+retained+source.substring(end),head.start+retained.length)
    }
    private fun removeEmptyExponent(power:Expr):Editor = removeTailKeepingHead(power.args[0],power.end)
    private fun emptyDenominatorAt(position:Int):Expr? = tree()?.nodes()?.filter {node->
        if(!fraction(node))false else {
            val denominator=node.args[1]
            val hole=if(denominator.kind=="group")denominator.args.firstOrNull() else denominator
            hole?.kind=="hole" && hole.start==position
        }
    }?.minByOrNull {it.end-it.start}
    private fun removeEmptyDenominator(fraction:Expr):Editor = removeTailKeepingHead(fraction.args[0],fraction.end)
    private fun selectedDenominator(start:Int,end:Int):Expr? = tree()?.nodes()?.filter {node->
        if(!fraction(node))false else {
            val denominator=node.args[1]
            (denominator.start==start && denominator.end==end) ||
                (denominator.kind=="group" && denominator.args.firstOrNull()?.let{it.start==start && it.end==end}==true)
        }
    }?.minByOrNull {it.end-it.start}
    private fun emptyMultiplicationOperandAt(position:Int):Pair<Expr,Int>? = tree()?.nodes()?.mapNotNull {node->
        if(node.kind!="binary" || node.value!="*")null else node.args.indexOfFirst {operand->
            operand.kind=="group" && operand.args.firstOrNull()?.kind=="hole" && operand.args[0].start==position
        }.takeIf {it>=0}?.let {node to it}
    }?.minByOrNull {it.first.end-it.first.start}
    private fun removeEmptyMultiplicationOperand(node:Expr,index:Int):Editor {
        val start=if(index==0)node.start else node.args[0].end
        val end=if(index==0)node.args[1].start else node.end
        return Editor(source.removeRange(start,end),start)
    }
    fun delete(): Editor {
        if(cursor!=anchor) {
            val start=minOf(cursor,anchor);val end=maxOf(cursor,anchor)
            selectedDenominator(start,end)?.let{return removeEmptyDenominator(it)}
            return remove(start,end)
        }
        fractionAfterDenominator(cursor)?.let {fraction->
            val denominator=fraction.args[1]
            val value=denominator.args[0]
            return when(value.kind) {
                "hole"->removeEmptyDenominator(fraction)
                "number","symbol"->Editor(source,denominator.end-1).delete()
                else->remove(value.start,value.end)
            }
        }
        emptyExponentAt(cursor)?.let{return removeEmptyExponent(it)}
        emptyDenominatorAt(cursor)?.let{return removeEmptyDenominator(it)}
        emptyMultiplicationOperandAt(cursor)?.let{return removeEmptyMultiplicationOperand(it.first,it.second)}
        powerAtExponentStart(cursor)?.let{return clearPowerBase(it)}
        return if(cursor<=0 || hiddenCallOpen(cursor-1) || hiddenFractionDelimiter(cursor-1) || emptyStructuredSlotDelimiter(cursor-1))this else remove(cursor-1,cursor)
    }
    fun deleteForward():Editor {
        if(cursor!=anchor) {
            val start=minOf(cursor,anchor);val end=maxOf(cursor,anchor)
            selectedDenominator(start,end)?.let{return removeEmptyDenominator(it)}
            return remove(start,end)
        }
        emptyExponentAt(cursor)?.let{return removeEmptyExponent(it)}
        emptyDenominatorAt(cursor)?.let{return removeEmptyDenominator(it)}
        emptyMultiplicationOperandAt(cursor)?.let{return removeEmptyMultiplicationOperand(it.first,it.second)}
        if(source.getOrNull(cursor)=='^')powerAtExponentStart(cursor+1)?.let{return clearPowerBase(it)}
        return if(cursor>=source.length || hiddenCallOpen(cursor) || hiddenFractionDelimiter(cursor) || emptyStructuredSlotDelimiter(cursor))this else remove(cursor,cursor+1)
    }
    private fun hiddenPowerBase(power:Expr):Expr? = power.args.getOrNull(0)?.takeIf {base->
        power.kind=="binary" && power.value=="^" && base.kind=="group" &&
            base.args.firstOrNull()?.kind in setOf("number","symbol","hole","call")
    }
    fun move(delta: Int): Editor {
        if(cursor!=anchor) {
            val position=if(delta>0)maxOf(cursor,anchor) else minOf(cursor,anchor)
            val scope=barePower(minOf(cursor,anchor),maxOf(cursor,anchor))?.args?.get(1)?.let{it.start..it.end}
            return Editor(source,position,exponent=scope)
        }
        if(delta>0&&exponent!=null&&cursor==exponent.last) {
            val power=barePower(exponent.first,exponent.last)
            return Editor(source,cursor,outside=power?.let{it.start..it.end})
        }
        if(cursor==source.length&&outside!=null&&delta>0)return this
        if(cursor==anchor && delta>0) {
            val power=tree()?.nodes()?.firstOrNull {node->hiddenPowerBase(node)?.end?.minus(1)==cursor}
            val exponent=power?.args?.get(1)
            if(exponent!=null) {
                if(exponent.kind=="group")return Editor(source,exponent.start+1)
                return Editor(source,exponent.start,exponent=exponent.start..exponent.end)
            }
        }
        if(cursor==anchor && delta<0) {
            val base=tree()?.nodes()?.firstNotNullOfOrNull {node->
                hiddenPowerBase(node)?.takeIf {node.args[1].start==cursor && source.getOrNull(cursor-1)=='^'}
            }
            if(base!=null)return Editor(source,base.end-1)
        }
        if(cursor==anchor && delta<0) {
            val power=tree()?.nodes()?.firstOrNull {node->
                node.kind=="binary" && node.value=="^" && node.args[1].kind=="group" &&
                    node.args[1].start+1==cursor
            }
            if(power!=null) {
                val base=power.args[0]
                return Editor(source,hiddenPowerBase(power)?.end?.minus(1) ?: base.end)
            }
        }
        if(cursor==anchor && delta>0 && tree()?.nodes()?.any {node->
            node.kind=="binary" && node.value=="^" && node.args[1].kind=="group" &&
                node.args[1].args.firstOrNull()?.kind=="hole" && node.args[1].end-1==cursor
        }==true)return this
        if(cursor==anchor && delta>0) {
            val fraction=tree()?.nodes()?.firstOrNull {node->fraction(node) &&
                node.args[0].end-(if(node.args[0].kind=="group")1 else 0)==cursor}
            if(fraction!=null) {
                val denominator=fraction.args[1]
                return Editor(source,denominator.start+if(denominator.kind=="group")1 else 0)
            }
        }
        if(cursor==anchor && delta<0) {
            val fraction=tree()?.nodes()?.firstOrNull {node->fraction(node) &&
                node.args[1].start+(if(node.args[1].kind=="group")1 else 0)==cursor}
            if(fraction!=null) {
                val numerator=fraction.args[0]
                return Editor(source,numerator.end-if(numerator.kind=="group")1 else 0)
            }
        }
        if(cursor==anchor && tree()?.nodes()?.any {node->fraction(node) &&
            (delta<0 && node.args[0].kind=="group" && node.args[0].args.firstOrNull()?.kind=="hole" && node.args[0].start+1==cursor ||
                delta>0 && node.args[1].kind=="group" && node.args[1].args.firstOrNull()?.kind=="hole" && node.args[1].end-1==cursor)
        }==true)return this
        if(cursor==source.length&&exponent==null) {
            val fraction=tree()?.let(::fractionEndingAtCursor)?.first
            if(fraction!=null) {
                val range=fraction.start..fraction.end
                if(delta>0&&outside==null)return Editor(source,cursor,outside=range)
                if(delta<0&&outside==range)return Editor(source,cursor)
            }
        }
        if(delta<0&&exponent==null) {
            val power=tree()?.nodes()?.filter{it.kind=="binary"&&it.value=="^"&&it.end==cursor&&it.args[1].kind!="group"}?.minByOrNull{it.end-it.start}
            if(power!=null)return Editor(source,cursor,exponent=power.args[1].let{it.start..it.end})
        }
        val position=(cursor+delta).coerceIn(0,source.length)
        if(delta>0&&cursor<source.length&&source[cursor]==')') {
            val container=tree()?.nodes()?.filter {it.kind=="binary"&&it.value in listOf("/","^")&&it.args[1].kind=="group"&&it.args[1].end==position}?.minByOrNull{it.end-it.start}
            if(container!=null)return Editor(source,position,outside=container.start..container.end)
        }
        return Editor(source,position,exponent=exponent?.takeIf{position in it})
    }
    private fun barePower(start:Int,end:Int):Expr?=tree()?.nodes()?.filter{
        it.kind=="binary"&&it.value=="^"&&it.args[1].kind!="group"&&start>=it.args[1].start&&end<=it.args[1].end
    }?.minByOrNull{it.args[1].end-it.args[1].start}
    private fun fractionEndingAtCursor(root:Expr):Pair<Expr,Expr>?=root.nodes().filter{
        it.kind=="binary"&&it.value=="/"&&it.end==cursor
    }.mapNotNull{fraction->
        fraction.args[1].nodes().filter{it.args.isEmpty()&&it.end==cursor&&it.start<it.end}
            .minByOrNull{it.end-it.start}?.let{fraction to it}
    }.minByOrNull{it.first.end-it.first.start}
    fun selectRange(start:Int,end:Int):Editor=copy(cursor=end,anchor=start,exponent=barePower(start,end)?.args?.get(1)?.let{it.start..it.end},outside=null,activeToken=null)
    fun placeInToken(start:Int,end:Int,position:Int)=Editor(source,position.coerceIn(start,end),exponent=barePower(start,end)?.args?.get(1)?.let{it.start..it.end},activeToken=start..end)
    fun select(node: Expr) = selectRange(node.start,node.end)
    fun after(start:Int,end:Int)=Editor(source,end,outside=start..end)
    /** Exactly one presentation node owns a collapsed cursor, including shared source boundaries. */
    fun cursorTarget():IntRange? {
        if(cursor!=anchor)return null
        outside?.let{return it}
        activeToken?.let {if(cursor in it)return it}
        val root=tree() ?: return runCatching{Lexer.scan(source).filter{it.text.isNotEmpty()&&cursor in it.start..it.end}.minByOrNull{if(it.end==cursor)0 else 1}?.let{it.start..it.end}}.getOrNull()
        if(cursor==source.length&&exponent==null) {
            root.nodes().firstOrNull{it.kind=="hole"&&it.start==cursor&&it.end==cursor}?.let{return it.start..it.end}
            fractionEndingAtCursor(root)?.second?.let{return it.start..it.end}
            return root.start..root.end
        }
        root.nodes().filter {fraction(it) && it.end==cursor && it.args[1].kind=="group" && it.args[1].end==cursor}
            .maxByOrNull {it.end-it.start}?.let{return it.start..it.end}
        if(exponent==null)root.nodes().filter {it.kind=="binary"&&it.value=="^"&&it.end==cursor}
            .maxByOrNull {it.end-it.start}?.let{return it.start..it.end}
        val leaves=root.nodes().filter{it.args.isEmpty()&&cursor in it.start..it.end}
        val leaf=leaves.sortedWith(compareBy<Expr>{if(it.kind=="hole")0 else if(it.end==cursor)1 else 2}.thenBy{it.end-it.start}).firstOrNull()
        if(leaf!=null)return leaf.start..leaf.end
        return root.nodes().filter{cursor in it.start..it.end}.minByOrNull{it.end-it.start}?.let{it.start..it.end}
    }
    fun tree(): Expr? = runCatching { Parser(source,true).parse() }.getOrNull()
    fun parent(): Editor {
        val node = tree()?.nodes()?.filter { it.start <= minOf(cursor,anchor) && it.end >= maxOf(cursor,anchor) && (it.start < minOf(cursor,anchor) || it.end > maxOf(cursor,anchor)) }?.minByOrNull { it.end-it.start }
        return node?.let(::select) ?: this
    }
    fun child(): Editor = tree()?.nodes()?.firstOrNull { it.start == minOf(cursor,anchor) && it.end == maxOf(cursor,anchor) }?.args?.firstOrNull()?.let(::select) ?: this
}
