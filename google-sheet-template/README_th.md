# Google Sheet Template

ไฟล์นี้ใช้เป็น template สำหรับสร้าง Google Sheet ที่เป็น source of truth ของข้อมูลสาขาในแอป `Branch Photo Vault`

## ไฟล์ที่ให้มา

- `GBKK4_List_template.csv`

## วิธีใช้งาน

1. เปิด Google Sheets
2. สร้าง spreadsheet ใหม่
3. ตั้งชื่อ spreadsheet ตามต้องการ เช่น `GBKK4_List`
4. เปลี่ยนชื่อชีตแรกเป็น `GBKK4_List`
5. นำเข้าไฟล์ `GBKK4_List_template.csv`

## โครงสร้างคอลัมน์

- `account`
- `branchCode`
- `shopName`
- `route`

## หมายเหตุ

- ใช้ `account + branchCode` เป็น key ของสาขา
- `rowNumber` ไม่ต้องสร้างเป็นคอลัมน์ในชีต เพราะเป็นค่าที่ Apps Script คำนวณจากเลขแถวแล้วส่งกลับใน API
- ถ้าต้องการเพิ่มคอลัมน์ภายในภายหลัง ควรให้ 4 คอลัมน์หลักนี้ยังคงอยู่และชื่อเดิมไม่เปลี่ยน
- ถ้าจะให้ Apps Script รองรับการอัปเดต route และเพิ่มสาขาได้ตรงกับแอป แนะนำให้ใช้ header แถวแรกตรงตามนี้ทุกตัวอักษร

## ข้อมูลตัวอย่าง

มีข้อมูลตัวอย่างให้ 5 แถว เพื่อใช้ทดสอบการ sync ครั้งแรก คุณสามารถลบทิ้งแล้วแทนด้วยข้อมูลจริงได้
