// The UI has one saved policy; engine requests carry their own snapshot.
let removed=false;
export const computationLimitsRemoved=()=>removed;
export function setComputationLimitsRemoved(value){removed=value===true;}
