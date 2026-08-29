/* One-day working exception for a recurring weekly day off */
let workdayOverrides=new Set();
const _loadWeeklySchedule_workdayOverride=loadWeeklySchedule;
loadWeeklySchedule=async function(){
  try{
    const z=await dayoffApi('weekly');
    weeklyWorkdays=Array.isArray(z.weekdays)?z.weekdays:[0,1,2,3,4,5,6];
    workdayOverrides=new Set(Array.isArray(z.workday_overrides)?z.workday_overrides:[]);
  }catch{
    weeklyWorkdays=[0,1,2,3,4,5,6];
    workdayOverrides=new Set();
  }
};
function isWorkdayOverride(ds){return workdayOverrides.has(ds)}
isDayOff=function(cal,ds){return isManualDayOff(cal,ds)||(isWeeklyOff(ds)&&!isWorkdayOverride(ds))};
const _toggleDayOff_workdayOverride=toggleDayOff;
toggleDayOff=async function(ds,currently){
  if(isWeeklyOff(ds)){
    if(isWorkdayOverride(ds)){
      if(!confirm('Вернуть '+new Date(ds+'T12:00:00').toLocaleDateString('ru-RU')+' в выходные по недельному графику?'))return;
      const z=await dayoffApi('unsetWorkdayOverride',{date:ds});
      workdayOverrides=new Set(z.workday_overrides||[]);
    }else{
      if(!confirm('Отменить выходной '+new Date(ds+'T12:00:00').toLocaleDateString('ru-RU')+' и сделать этот день рабочим?'))return;
      const z=await dayoffApi('setWorkdayOverride',{date:ds});
      workdayOverrides=new Set(z.workday_overrides||[]);
    }
    await renderCal();
    return;
  }
  return _toggleDayOff_workdayOverride(ds,currently);
};
const _renderDayV4_workdayOverride=renderDayV4;
renderDayV4=async function(){
  await _renderDayV4_workdayOverride();
  if(!isWeeklyOff(focus))return;
  const dv=document.getElementById('dayview');
  if(!dv)return;
  document.querySelectorAll('.dayoff-daybar').forEach(x=>x.remove());
  const override=isWorkdayOverride(focus);
  const bar=document.createElement('div');
  bar.className='dayoff-daybar';
  bar.innerHTML='<div class="dayoff-status"><b>'+(override?'Рабочий день — исключение':'Выходной по недельному графику')+'</b><span>'+(override?'Обычно этот день выходной':'Можно отменить только для этой даты')+'</span></div><button type="button" class="btn '+(override?'':'primary')+'" id="dayoffOverrideBtn">'+(override?'Вернуть выходной по графику':'Отменить выходной — сделать рабочим')+'</button>';
  const strip=dv.querySelector('.day-date-strip');
  if(strip)strip.insertAdjacentElement('beforebegin',bar);else dv.insertBefore(bar,dv.firstChild);
  document.getElementById('dayoffOverrideBtn').onclick=()=>toggleDayOff(focus,!override);
};
