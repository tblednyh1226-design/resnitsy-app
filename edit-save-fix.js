/* Appointment edit/save hotfix: avoid implicit element globals on mobile browsers. */
window.form = function(a=null, ds=focus, startTime='16:00') {
  let cid = a?.client_id || '';
  let rows = a?.services?.length
    ? a.services.map(s => ({
        service_id: s.service_id || '',
        name: s.name || '',
        standard_price: Number(s.standard_price ?? s.price ?? 0),
        price: Number(s.price ?? s.standard_price ?? 0),
        duration: Number(s.duration ?? s.duration_minutes ?? 60)
      }))
    : [];

  const firstActive = services.find(s => s.is_active);
  if (!rows.length && firstActive) {
    rows = [{
      service_id:firstActive.id,
      name:firstActive.name,
      standard_price:Number(firstActive.base_price||0),
      price:Number(firstActive.base_price||0),
      duration:Number(firstActive.duration_minutes||60)
    }];
  }

  modal(a ? 'Редактирование записи' : 'Новая запись','',
    '<div class="field"><label>Клиент</label><input id="q" placeholder="Имя или телефон" value="'+(a?.client_name||'')+'"></div>'+
    '<div id="res" class="list"></div>'+
    '<div class="twocol"><div class="field"><label>Дата</label><input id="date" type="date" value="'+(a?ld(a.starts_at):ds)+'"></div>'+
    '<div class="field"><label>Время</label><input id="time" type="time" step="900" value="'+(a?hm(a.starts_at):startTime)+'"></div></div>'+
    '<div id="svcRows"></div><button class="btn" id="addSvc" style="width:100%">+ Добавить услугу</button>'+
    '<div class="field"><label>Комментарий</label><textarea id="com">'+(a?.master_comment||'')+'</textarea></div>'+
    '<div id="saveError" class="sub" style="color:#a33;min-height:18px"></div>',
    '<button class="btn primary" id="save">Сохранить</button><button class="btn" id="newC">+ Новый клиент</button>'
  );

  const qEl=document.getElementById('q');
  const resEl=document.getElementById('res');
  const svcRowsEl=document.getElementById('svcRows');
  const addSvcEl=document.getElementById('addSvc');
  const newCEl=document.getElementById('newC');
  const saveEl=document.getElementById('save');
  const dateEl=document.getElementById('date');
  const timeEl=document.getElementById('time');
  const comEl=document.getElementById('com');
  const errEl=document.getElementById('saveError');

  function renderRows(){
    svcRowsEl.innerHTML='';
    rows.forEach((r,i)=>{
      const x=document.createElement('div');
      x.className='card';
      const active=services.filter(s=>s.is_active || s.id===r.service_id);
      x.innerHTML='<div class="field"><select class="ss">'+active.map(s=>'<option value="'+s.id+'" '+(s.id===r.service_id?'selected':'')+'>'+s.name+'</option>').join('')+'</select></div>'+
        '<div class="twocol"><div class="field"><label>Цена</label><input class="sp" type="number" value="'+Number(r.price||0)+'"></div>'+
        '<div class="field"><label>Длительность, мин</label><input class="sd" type="number" value="'+Number(r.duration||60)+'"></div></div>';
      const ss=x.querySelector('.ss'), sp=x.querySelector('.sp'), sd=x.querySelector('.sd');
      ss.addEventListener('change',()=>{
        const s=services.find(z=>z.id===ss.value); if(!s) return;
        rows[i]={service_id:s.id,name:s.name,standard_price:Number(s.base_price||0),price:Number(s.base_price||0),duration:Number(s.duration_minutes||60)};
        renderRows();
      });
      sp.addEventListener('input',()=>rows[i].price=Number(sp.value||0));
      sd.addEventListener('input',()=>rows[i].duration=Math.max(1,Number(sd.value||60)));
      svcRowsEl.appendChild(x);
    });
  }
  renderRows();

  qEl.addEventListener('input',async()=>{
    const val=qEl.value.trim();
    if(val.length<2){resEl.innerHTML=''; return;}
    try{
      const z=await rpc('master_app_clients',{p_pin:PIN,p_q:val});
      resEl.innerHTML=z.map(c=>'<button type="button" class="card pick" data-id="'+c.id+'"><b>'+c.name+'</b><div class="sub">'+(c.phone||'')+'</div></button>').join('');
      [...resEl.querySelectorAll('.pick')].forEach(b=>b.addEventListener('click',()=>{cid=b.dataset.id;qEl.value=b.querySelector('b').textContent;resEl.innerHTML='';}));
    }catch(e){errEl.textContent=e.message||'Ошибка поиска клиента';}
  });

  addSvcEl.addEventListener('click',()=>{
    const s=services.find(z=>z.is_active); if(!s) return alert('Нет активных услуг');
    rows.push({service_id:s.id,name:s.name,standard_price:Number(s.base_price||0),price:Number(s.base_price||0),duration:Number(s.duration_minutes||60)});
    renderRows();
  });
  newCEl.addEventListener('click',()=>newClient(c=>{cid=c.id;qEl.value=c.name;}));

  saveEl.addEventListener('click',async()=>{
    errEl.textContent='';
    if(!cid){errEl.textContent='Выберите клиента из списка'; return;}
    if(!dateEl.value || !timeEl.value){errEl.textContent='Укажите дату и время'; return;}
    if(!rows.length){errEl.textContent='Добавьте услугу'; return;}
    saveEl.disabled=true; saveEl.textContent='Сохраняю…';
    try{
      await rpc('master_app_save',{
        p_pin:PIN,
        p_id:a?.id||null,
        p_client:cid,
        p_start:new Date(dateEl.value+'T'+timeEl.value+':00+03:00').toISOString(),
        p_services:rows,
        p_comment:comEl.value,
        p_status:a?.status||'new'
      });
      closeModal();
      await renderCal();
    }catch(e){
      errEl.textContent=e.message||'Не удалось сохранить запись';
      saveEl.disabled=false; saveEl.textContent='Сохранить';
    }
  });
};
