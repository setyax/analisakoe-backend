ANALISAKOE - MASTER DATA LIST TEMPLATE REPAIR

Isi paket:
- templates/ui/customers/list.html
- templates/ui/suppliers/list.html
- templates/ui/uoms/list.html
- templates/ui/warehouses/list.html
- Install-MasterLists.ps1

Apa yang diperbaiki:
Keempat list.html yang semula berisi salinan halaman Sales Order dibuat ulang agar menggunakan model dan DTO masing-masing:
- customers.content: CustomerResponse (code, name, taxId, currency, creditLimit, paymentTermDays, status)
- suppliers.content: SupplierResponse (code, name, taxId, currency, paymentTermDays, status)
- uoms.content: UomResponse (code, name)
- warehouses.content: WarehouseResponse (code, name, address, status)

Semua URL memakai Thymeleaf @{} sehingga context path /api akan ditambahkan otomatis.
Tombol Add, Edit, dan Delete mengarah ke endpoint CRUD masing-masing Controller.

CARA INSTALL:
1. Extract ZIP ini ke folder sementara, misalnya C:\Temp\MasterListFix.
2. Buka PowerShell.
3. Jalankan:
   Set-ExecutionPolicy -Scope Process Bypass
   .\Install-MasterLists.ps1
4. Script membuat backup empat file lama di folder backup_master_lists_fix_YYYYMMDD_HHMMSS.
5. Restart aplikasi AnalisaKoe jika template tidak otomatis dimuat ulang.

CATATAN:
- Script hanya mengganti empat file list.html.
- UiSalesOrderController.java dan templates/ui/sales/order_list.html tidak disentuh.
- Model PageResponse diasumsikan mengekspos koleksi melalui properti `content`, pola standar hasil PageResponse.of(Page). Class PageResponse.java tidak termasuk di source ZIP yang dikirim; apabila aplikasi Anda memakai nama properti koleksi berbeda, bagian `.content` pada empat template perlu disesuaikan.
- Penghapusan memakai DELETE endpoint UI yang sudah ada pada masing-masing controller. Tidak ada perubahan API atau database.
