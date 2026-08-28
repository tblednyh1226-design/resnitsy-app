/* Keep the current day heading visible until the next day is fully rendered. */
(function(){
  const previous=window.renderDayV4;
  if(typeof previous!=='function')return;
  window.renderDayV4=async function(){
    const period=document.getElementById('period');
    const oldHTML=period?period.innerHTML:'';
    const oldMin=period?period.style.minHeight:'';
    if(period){
      const h=period.getBoundingClientRect().height;
      if(h)period.style.minHeight=h+'px';
      period.innerHTML=oldHTML;
    }
    await previous.apply(this,arguments);
    if(period){
      period.innerHTML='<span class="period-weekday">'+ruDate(focus,{weekday:'long'})+'</span><span class="period-date">'+ruDate(focus,{day:'numeric',month:'long'})+'</span>';
      period.style.minHeight=oldMin;
    }
  };
})();
