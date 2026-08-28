/* Day slot UX: tap slot to create appointment, tap state pill to toggle online availability. */
(function(){
  const previous=window.renderDayV4;
  if(typeof previous!=='function')return;

  window.renderDayV4=async function(){
    const renderedDay=focus;
    const out=await previous.apply(this,arguments);

    document.querySelectorAll('#dayview .day-slot').forEach(btn=>{
      const raw=(btn.textContent||'').trim();
      const match=raw.match(/\b\d{2}:\d{2}\b/);
      if(!match)return;
      const time=match[0];
      const isAvailable=btn.classList.contains('available');

      btn.innerHTML='<span class="slot-book-time">'+time+'</span><span class="slot-state-pill '+(isAvailable?'on':'off')+'">'+(isAvailable?'Доступно':'Закрыто')+'</span>';
      btn.setAttribute('aria-label','Записать клиента на '+time);

      btn.onclick=async function(e){
        const state=e.target.closest('.slot-state-pill');
        if(state){
          e.preventDefault();
          e.stopPropagation();
          const iso=new Date(renderedDay+'T'+time+':00+03:00').toISOString();
          await rpc('master_app_set_availability',{p_pin:PIN,p_slot:iso,p_value:!isAvailable});
          await window.renderDayV4();
          return;
        }
        e.preventDefault();
        e.stopPropagation();
        form(null,renderedDay,time);
      };
    });
    return out;
  };
})();
