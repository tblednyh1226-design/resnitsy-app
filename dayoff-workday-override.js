/* One-day working exception for a recurring weekly day off */
let workdayOverrides=new Set(),workdayOverrideGroups=[];
loadWeeklySchedule=async function(){
  try{
    const z=await dayoffApi('weekly');
    weeklyWorkdays=Array.isArray(z.weekdays)?z.weekdays:[0,1,2,3,4,5,6];
    workdayOverrides=new Set(Array.isArray(z.workday_overrides)?z.workday_overrides:[]);
    workdayOverrideGroups=Array.isArray(z.work_time_groups)?z.work_time_groups:[];
  }catch{
    weeklyWorkdays=[0,1,2,3,4,5,6];workdayOverrides=new Set();workdayOverrideGroups=[];
  }
};
function isWorkdayOverride(ds){return workdayOverrides.has(ds)}
function overrideFallbackTimes(){if(!workdayOverrideGroups.length)return ['10:00','13:00','16:00','19:00'];const g=[...workdayOverrideGroups].sort((a,b)=>(b.days?.length||0)-(a.days?.length||0))[0];return [...new Set((g.times||[]).map(String))].sort()}
isDayOff=function(cal,ds){return isManualDayOff(cal,ds)||(isWeeklyOff(ds)&&!isWorkdayOverride(ds))};
const _toggleDayOff_workdayOverride=toggleDayOff;
toggleDayOff=async function(ds,currently){
  if(isWeeklyOff(ds)){
    if(isWorkdayOverride(ds)){
      if(!confirm('Вернуть '+new Date(ds+'T12:00:00').toLocaleDateString('ru-RU')+' в выходные по недельному графику?'))return;
      const z=await dayoffApi('unsetWorkdayOverride',{date:ds});workdayOverrides=new Set(z.workday_overrides||[]);
    }else{
      if(!confirm('Отменить выходной '+new Date(ds+'T12:00:00').toLocaleDateString('ru-RU')+' и сделать этот день рабочим?'))return;
      const z=await dayoffApi('setWorkdayOverride',{date:ds});workdayOverrides=new Set(z.workday_overrides||[]);
    }
    await renderCal();return;
  }
  return _toggleDayOff_workdayOverride(ds,currently);
};
const _renderDayV4_workdayOverride=renderDayV4;
renderDayV4=async function(){
  await loadWeeklySchedule();
  await _renderDayV4_workdayOverride();
  if(!isWeeklyOff(focus))return;
  const dv=document.getElementById('dayview');if(!dv)return;
  document.querySelectorAll('.dayoff-daybar').forEach(x=>x.remove());
  const override=isWorkdayOverride(focus),manual=isManualDayOff({blocks:lastCal?.blocks||[]},focus);
  const bar=document.createElement('div');bar.className='dayoff-daybar';
  if(manual){bar.innerHTML='<div class="dayoff-status"><b>Выходной</b><span>Для этой даты также установлен разовый выходной</span></div>'}
  else bar.innerHTML='<div class="dayoff-status"><b>'+(override?'Рабочий день — исключение':'Выходной по недельному графику')+'</b><span>'+(override?'Обычно этот день выходной':'Можно отменить только для этой даты')+'</span></div><button type="button" class="btn '+(override?'':'primary')+'" id="dayoffOverrideBtn">'+(override?'Вернуть выходной по графику':'Отменить выходной — сделать рабочим')+'</button>';
  const strip=dv.querySelector('.day-date-strip');if(strip)strip.insertAdjacentElement('beforebegin',bar);else dv.insertBefore(bar,dv.firstChild);
  const b=document.getElementById('dayoffOverrideBtn');if(b)b.onclick=()=>toggleDayOff(focus,!override);
};
const _renderWeekV4_workdayOverride=renderWeekV4;
renderWeekV4=async function(){
  await loadWeeklySchedule();
  const out=await _renderWeekV4_workdayOverride.apply(this,arguments),cal=lastCal,h=Math.max(420,document.getElementById('week')?.clientHeight||520),fallback=overrideFallbackTimes();
  [...document.querySelectorAll('#week .day')].forEach((col,i)=>{
    const ds=add(weekStart,i);if(!isWorkdayOverride(ds)||isManualDayOff(cal,ds))return;
    col.classList.remove('day-off');col.querySelectorAll('.dayoff-mark').forEach(x=>x.remove());
    if(col.querySelector('.free'))return;
    const aps=(cal?.appointments||[]).filter(a=>ld(a.starts_at)===ds&&a.status!=='cancelled');
    fallback.forEach(t=>{const iso=new Date(ds+'T'+t+':00+03:00').toISOString(),moment=new Date(iso);if(aps.some(a=>moment>=new Date(a.starts_at)&&moment<new Date(a.ends_at)))return;const m=Number(t.slice(0,2))*60+Number(t.slice(3)),b=document.createElement('button');b.className='free';b.style.top=(42+(m-540)/840*(h-42))+'px';b.textContent=t;b.onclick=e=>{e.stopPropagation();focus=ds;view='day';renderCal()};col.appendChild(b)})
  });
  return out;
};
