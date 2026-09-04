from pathlib import Path

p = Path('app/src/main/java/ru/slotelly/app/EnhancedMoreScreen.kt')
s = p.read_text(encoding='utf-8')

old_type = 'private fun notificationType(r:JsonObject):String=when((r.get("template_type")?:r.get("type"))?.takeUnless{it.isJsonNull}?.asString?.lowercase()){ "confirmation","confirm","booking_confirmation"->"Подтверждение записи";"reminder","appointment_reminder"->"Напоминание о записи";"reschedule","rescheduled","move"->"Перенос записи";"cancellation","cancel","cancelled","canceled"->"Отмена записи";"payment","payment_reminder"->"Напоминание об оплате";else->"Уведомление клиенту"}'
new_type = 'private fun notificationType(r:JsonObject):String=when((r.get("template_type")?:r.get("type"))?.takeUnless{it.isJsonNull}?.asString?.lowercase()){ "confirmation","confirm","booking_confirmation"->"Подтверждение записи";"reminder","appointment_reminder"->"Напоминание о записи";"reminder_day_before"->"Напоминание накануне";"reminder_hours_before"->"Напоминание перед записью";"reschedule","rescheduled","move"->"Перенос записи";"cancellation","cancel","cancelled","canceled"->"Отмена записи";"payment","payment_reminder"->"Напоминание об оплате";else->"Уведомление клиенту"}'
if old_type not in s:
    raise SystemExit('notificationType marker not found')
s = s.replace(old_type, new_type, 1)

start = s.index('private fun notificationClient(r:JsonObject):String{')
end = s.index('\nprivate fun channelLabel', start)
new_client = '''private fun notificationClient(r:JsonObject):String{
    for(k in listOf("client_name","client_display_name","display_name","recipient_name","customer_name","name")){
        val v=r.get(k)?.takeUnless{it.isJsonNull}?.asString?.trim().orEmpty()
        if(v.isNotBlank())return v
    }
    val nested=runCatching{r.getAsJsonObject("clients")}.getOrNull()
    if(nested!=null){
        val display=nested.get("display_name")?.takeUnless{it.isJsonNull}?.asString?.trim().orEmpty()
        if(display.isNotBlank())return display
        val first=nested.get("first_name")?.takeUnless{it.isJsonNull}?.asString?.trim().orEmpty()
        val last=nested.get("last_name")?.takeUnless{it.isJsonNull}?.asString?.trim().orEmpty()
        val full=listOf(first,last).filter{it.isNotBlank()}.joinToString(" ")
        if(full.isNotBlank())return full
    }
    var phone=""
    for(k in listOf("recipient_phone","phone","client_phone")){
        val v=r.get(k)?.takeUnless{it.isJsonNull}?.asString?.trim().orEmpty()
        if(v.isNotBlank()){phone=v;break}
    }
    return if(phone.isNotBlank())"Клиент · $phone" else "Клиент"
}
'''
s = s[:start] + new_client + s[end:]
p.write_text(s, encoding='utf-8')

main = Path('app/src/main/java/ru/slotelly/app/NativeMainActivity.kt')
m = main.read_text(encoding='utf-8')
m = m.replace('0.6.1 WORK','0.6.3 WORK').replace('0.6 WORK','0.6.3 WORK')
main.write_text(m, encoding='utf-8')
