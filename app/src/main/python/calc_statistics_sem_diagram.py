"""Shared, scrollable SEM diagram geometry for Android and Web."""
def diagrams(value,labels):
    groups=value.get('Group summary') or [{'n':value['n']}]
    series=[]
    for group in groups:
        selected=lambda key:[row for row in value.get(key,[]) if row.get('Group')==group.get('Group')]
        loadings=selected('Loadings'); paths=selected('Structural paths'); correlations=selected('Exogenous correlations')
        latent=selected('Latent R²')
        indicator_r2={int(row['Indicator']):float(row['R²']) for row in selected('Indicator R²')}
        if not latent or not loadings: continue
        # Each observed indicator has one rectangle even with cross-loadings.
        indicators={row['Indicator']:row for row in reversed(loadings)}
        gutter=180+36*(len(paths)+len(correlations)); fx=gutter+70; ox=fx+430
        nodes=[]; lookup={}; y=60
        for factor in latent:
            observed=[(i,row) for i,row in sorted(indicators.items()) if row['Factor']==factor['Factor']]
            height=max(150,len(observed)*100)
            node={'id':'f'+str(factor['Factor']),'kind':'latent','label':'Factor '+str(factor['Factor']),
                  'x':fx,'y':y+height/2,'r2':float(factor['R²']) if factor['Role']=='Endogenous' else None}
            nodes.append(node); lookup[node['id']]=node
            for index,(i,row) in enumerate(observed):
                label=labels.get('feature:'+str(i),row['term'])
                if label=='feature:'+str(i): label='Feature '+str(i)
                node={'id':'x'+str(i),'kind':'observed','label':label,'x':ox,'y':y+(index+.5)*height/len(observed),'r2':indicator_r2.get(int(i))}
                nodes.append(node); lookup[node['id']]=node
            y+=height+60
        edges=[]
        for row in loadings:
            source=lookup['f'+str(row['Factor'])]; target=lookup['x'+str(row['Indicator'])]
            start=[source['x']+70,source['y']]; end=[target['x']-70,target['y']]
            edges.append({'kind':'loading','source':source['id'],'target':target['id'],'start':start,'end':end,
                          'controls':[[start[0]+125,start[1]],[end[0]-125,end[1]]],
                          'labelPosition':[(start[0]+end[0])/2,(start[1]+end[1])/2],
                          'estimate':float(row['Standardized loading']),'interval':list(map(float,row['Standardized CI95']))})
        for index,row in enumerate(paths+correlations):
            covariance=index>=len(paths); ids=row['term'].split(' ↔ ' if covariance else ' → ')
            source=lookup['f'+ids[0]]; target=lookup['f'+ids[1]]
            start=[fx-70,source['y']]; end=[fx-70,target['y']]; controlx=20+index*36
            edges.append({'kind':'covariance' if covariance else 'path','source':source['id'],'target':target['id'],
                          'start':start,'end':end,'controls':[[controlx,start[1]],[controlx,end[1]]],
                          'labelPosition':[.25*start[0]+.75*controlx,(start[1]+end[1])/2],
                          'estimate':float(row['Correlation'] if covariance else row['Standardized path']),
                          'interval':None if covariance else list(map(float,row['Standardized CI95']))})
        # Place captions on their own curves, away from nodes and other
        # captions. Different paths can have identical vertical midpoints.
        occupied=[(n['x']-74,n['y']-(41 if n['kind']=='latent' else 29),
                   n['x']+74,n['y']+(41 if n['kind']=='latent' else 29)) for n in nodes]
        for edge in edges:
            points=[edge['start'],*edge['controls'],edge['end']]
            for t in (.5,.35,.65,.2,.8,.45,.55,.25,.75,.1,.9):
                weights=((1-t)**3,3*(1-t)**2*t,3*(1-t)*t*t,t**3)
                cx,cy=[sum(w*point[axis] for w,point in zip(weights,points)) for axis in range(2)]
                box=(cx-63,cy-26,cx+63,cy+(20 if edge['interval'] else 6))
                if not any(box[0]<right and box[2]>left and box[1]<bottom and box[3]>top for left,top,right,bottom in occupied):
                    edge['labelPosition']=[cx,cy];occupied.append(box);break
        series.append({'label':group.get('Group','All observations'),'n':int(group['n']),'nodes':nodes,'edges':edges,'width':ox+130,'height':y})
    if not series: return []
    plot={'kind':'sem-diagram','title':'Structural equation diagram',**series[0]}
    if len(series)>1: plot['series']=series
    return [plot]
