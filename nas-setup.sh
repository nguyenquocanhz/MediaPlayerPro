#!/bin/bash
#==============================================================================
# NAS Server Auto Setup Script
# - Tự động cài Samba nếu chưa có
# - Hiển thị phân vùng trống (LVM + disk chưa mount)
# - Tạo phân vùng LVM mới hoặc mount phân vùng có sẵn
# - Cấu hình Samba share tự động
#
# Sử dụng: sudo bash nas-setup.sh
#==============================================================================

set -e

# Màu sắc
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

#--- Hàm tiện ích ---
print_header() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}  ${BOLD}$1${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════╝${NC}"
}

print_info() { echo -e "  ${BLUE}[INFO]${NC} $1"; }
print_ok() { echo -e "  ${GREEN}[✓]${NC} $1"; }
print_warn() { echo -e "  ${YELLOW}[!]${NC} $1"; }
print_err() { echo -e "  ${RED}[✗]${NC} $1"; }

confirm() {
    read -rp "  → $1 (y/n): " choice
    [[ "$choice" =~ ^[Yy]$ ]]
}

#--- Kiểm tra root ---
if [[ $EUID -ne 0 ]]; then
    print_err "Script cần chạy với quyền root!"
    echo "  Sử dụng: sudo bash $0"
    exit 1
fi

REAL_USER=${SUDO_USER:-$USER}

print_header "🖥️  NAS Server Auto Setup"
echo -e "  User: ${GREEN}$REAL_USER${NC}"
echo -e "  Hostname: ${GREEN}$(hostname)${NC}"
echo -e "  Thời gian: ${GREEN}$(date '+%Y-%m-%d %H:%M:%S')${NC}"

#==============================================================================
# BƯỚC 1: Cài đặt Samba
#==============================================================================
print_header "📦  Bước 1: Kiểm tra & Cài đặt Samba"

if command -v smbd &>/dev/null; then
    SAMBA_VER=$(smbd --version 2>/dev/null || echo "unknown")
    print_ok "Samba đã cài đặt: $SAMBA_VER"
else
    print_warn "Samba chưa được cài đặt"
    if confirm "Cài đặt Samba ngay?"; then
        print_info "Đang cài đặt Samba..."
        apt update -qq
        apt install -y samba samba-common-bin smbclient
        systemctl enable smbd
        systemctl start smbd
        print_ok "Samba đã cài đặt thành công!"
    else
        print_err "Không thể tiếp tục nếu không có Samba. Thoát."
        exit 1
    fi
fi

# Tạo Samba user nếu chưa có
if ! pdbedit -L 2>/dev/null | grep -q "^${REAL_USER}:"; then
    print_warn "User '$REAL_USER' chưa có trong Samba"
    if confirm "Tạo Samba password cho '$REAL_USER'?"; then
        smbpasswd -a "$REAL_USER"
        print_ok "Đã tạo Samba user: $REAL_USER"
    fi
else
    print_ok "Samba user '$REAL_USER' đã tồn tại"
fi

#==============================================================================
# BƯỚC 2: Hiển thị phân vùng
#==============================================================================
print_header "💾  Bước 2: Phân tích dung lượng"

echo ""
echo -e "  ${BOLD}── Phân vùng đang mount ──${NC}"
df -h --output=source,size,used,avail,pcent,target | grep -v "tmpfs\|loop\|udev" | head -20
echo ""

# LVM free space
echo -e "  ${BOLD}── LVM Volume Groups ──${NC}"
VG_LIST=$(vgs --noheadings -o vg_name 2>/dev/null | tr -d ' ')
if [[ -n "$VG_LIST" ]]; then
    for vg in $VG_LIST; do
        VG_FREE=$(vgs --noheadings -o vg_free --units g "$vg" 2>/dev/null | tr -d ' ')
        VG_SIZE=$(vgs --noheadings -o vg_size --units g "$vg" 2>/dev/null | tr -d ' ')
        echo -e "  VG: ${GREEN}$vg${NC} | Tổng: ${VG_SIZE} | ${YELLOW}Trống: ${VG_FREE}${NC}"
    done
else
    print_info "Không tìm thấy LVM Volume Group"
fi
echo ""

# Phân vùng chưa mount
echo -e "  ${BOLD}── Phân vùng CHƯA mount ──${NC}"
UNMOUNTED=()
while IFS= read -r line; do
    DEV=$(echo "$line" | awk '{print $1}')
    SIZE=$(echo "$line" | awk '{print $4}')
    TYPE=$(echo "$line" | awk '{print $6}')
    MOUNT=$(echo "$line" | awk '{print $7}')
    
    if [[ "$TYPE" == "part" && -z "$MOUNT" && ! "$DEV" =~ "loop" ]]; then
        FSTYPE=$(blkid -o value -s TYPE "/dev/$DEV" 2>/dev/null || echo "không có")
        UNMOUNTED+=("/dev/$DEV|$SIZE|$FSTYPE")
        echo -e "  ${YELLOW}/dev/$DEV${NC} | Size: ${SIZE} | FS: ${FSTYPE}"
    fi
done < <(lsblk -rno NAME,MAJ,MIN,SIZE,RO,TYPE,MOUNTPOINT 2>/dev/null)

if [[ ${#UNMOUNTED[@]} -eq 0 ]]; then
    print_info "Không có phân vùng nào chưa mount"
fi

#==============================================================================
# BƯỚC 3: Chọn phương thức tạo share
#==============================================================================
print_header "🔧  Bước 3: Tạo NAS Share"

echo ""
echo -e "  ${BOLD}Chọn phương thức:${NC}"
echo -e "  ${GREEN}1)${NC} Tạo phân vùng LVM mới (từ dung lượng trống VG)"
echo -e "  ${GREEN}2)${NC} Mount phân vùng có sẵn chưa mount (sdb1, sdb2...)"
echo -e "  ${GREEN}3)${NC} Dùng thư mục có sẵn"
echo -e "  ${GREEN}4)${NC} Xem các share hiện tại"
echo -e "  ${GREEN}0)${NC} Thoát"
echo ""
read -rp "  → Chọn (0-4): " METHOD

case $METHOD in
#----------------------------------------------------------------------
# Phương thức 1: Tạo LVM mới
#----------------------------------------------------------------------
1)
    print_header "📀  Tạo phân vùng LVM mới"
    
    if [[ -z "$VG_LIST" ]]; then
        print_err "Không tìm thấy Volume Group. Không thể tạo LVM."
        exit 1
    fi
    
    # Chọn VG nếu có nhiều
    if [[ $(echo "$VG_LIST" | wc -l) -gt 1 ]]; then
        echo "  Volume Groups có sẵn:"
        i=1
        for vg in $VG_LIST; do
            VG_FREE=$(vgs --noheadings -o vg_free --units g "$vg" 2>/dev/null | tr -d ' ')
            echo -e "  ${GREEN}$i)${NC} $vg (trống: $VG_FREE)"
            ((i++))
        done
        read -rp "  → Chọn VG: " VG_CHOICE
        SELECTED_VG=$(echo "$VG_LIST" | sed -n "${VG_CHOICE}p")
    else
        SELECTED_VG="$VG_LIST"
    fi
    
    VG_FREE_NUM=$(vgs --noheadings -o vg_free --units g --nosuffix "$SELECTED_VG" 2>/dev/null | tr -d ' ' | cut -d'.' -f1)
    print_info "VG '$SELECTED_VG' còn trống: ~${VG_FREE_NUM}G"
    
    if [[ "$VG_FREE_NUM" -lt 1 ]]; then
        print_err "Không đủ dung lượng trống trong VG!"
        exit 1
    fi
    
    read -rp "  → Tên share (vd: media, backup, data): " SHARE_NAME
    SHARE_NAME=$(echo "$SHARE_NAME" | tr '[:upper:]' '[:lower:]' | tr ' ' '-' | tr -cd 'a-z0-9-')
    
    if [[ -z "$SHARE_NAME" ]]; then
        print_err "Tên share không hợp lệ!"
        exit 1
    fi
    
    # Kiểm tra LV đã tồn tại chưa
    if lvs "$SELECTED_VG/$SHARE_NAME-lv" &>/dev/null; then
        print_err "Logical Volume '$SHARE_NAME-lv' đã tồn tại!"
        exit 1
    fi
    
    read -rp "  → Dung lượng (GB, tối đa ${VG_FREE_NUM}G): " LV_SIZE
    
    if [[ "$LV_SIZE" -gt "$VG_FREE_NUM" ]]; then
        print_err "Vượt quá dung lượng trống ($VG_FREE_NUM G)!"
        exit 1
    fi
    
    MOUNT_POINT="/mnt/$SHARE_NAME"
    
    echo ""
    echo -e "  ${BOLD}Xác nhận:${NC}"
    echo -e "  - LV: ${GREEN}${SHARE_NAME}-lv${NC} (${LV_SIZE}G)"
    echo -e "  - VG: ${GREEN}${SELECTED_VG}${NC}"
    echo -e "  - Mount: ${GREEN}${MOUNT_POINT}${NC}"
    echo -e "  - Samba share: ${GREEN}[${SHARE_NAME^^}]${NC}"
    echo ""
    
    if confirm "Tiến hành tạo?"; then
        print_info "Tạo Logical Volume..."
        lvcreate -L "${LV_SIZE}G" -n "${SHARE_NAME}-lv" "$SELECTED_VG"
        print_ok "LV đã tạo: /dev/$SELECTED_VG/${SHARE_NAME}-lv"
        
        print_info "Format ext4..."
        mkfs.ext4 -L "${SHARE_NAME^^}" "/dev/$SELECTED_VG/${SHARE_NAME}-lv"
        print_ok "Format hoàn tất"
        
        print_info "Mount phân vùng..."
        mkdir -p "$MOUNT_POINT"
        mount "/dev/$SELECTED_VG/${SHARE_NAME}-lv" "$MOUNT_POINT"
        print_ok "Mounted tại $MOUNT_POINT"
        
        # Thêm vào fstab
        if ! grep -q "${SHARE_NAME}-lv" /etc/fstab; then
            echo "/dev/$SELECTED_VG/${SHARE_NAME}-lv  $MOUNT_POINT  ext4  defaults  0  2" >> /etc/fstab
            print_ok "Đã thêm vào /etc/fstab (auto-mount)"
        fi
        
        # Phân quyền
        chown -R "$REAL_USER:$REAL_USER" "$MOUNT_POINT"
        chmod -R 775 "$MOUNT_POINT"
        print_ok "Phân quyền cho user '$REAL_USER'"
    else
        print_warn "Đã hủy."
        exit 0
    fi
    ;;

#----------------------------------------------------------------------
# Phương thức 2: Mount phân vùng có sẵn
#----------------------------------------------------------------------
2)
    print_header "💿  Mount phân vùng có sẵn"
    
    if [[ ${#UNMOUNTED[@]} -eq 0 ]]; then
        print_err "Không có phân vùng nào chưa mount!"
        exit 1
    fi
    
    echo "  Phân vùng chưa mount:"
    i=1
    for entry in "${UNMOUNTED[@]}"; do
        IFS='|' read -r DEV SIZE FSTYPE <<< "$entry"
        echo -e "  ${GREEN}$i)${NC} $DEV | Size: $SIZE | FS: $FSTYPE"
        ((i++))
    done
    
    read -rp "  → Chọn phân vùng (1-${#UNMOUNTED[@]}): " PART_CHOICE
    SELECTED_ENTRY="${UNMOUNTED[$((PART_CHOICE-1))]}"
    IFS='|' read -r SELECTED_DEV SELECTED_SIZE SELECTED_FS <<< "$SELECTED_ENTRY"
    
    read -rp "  → Tên share (vd: media, backup): " SHARE_NAME
    SHARE_NAME=$(echo "$SHARE_NAME" | tr '[:upper:]' '[:lower:]' | tr ' ' '-' | tr -cd 'a-z0-9-')
    MOUNT_POINT="/mnt/$SHARE_NAME"
    
    # Format nếu chưa có filesystem
    if [[ "$SELECTED_FS" == "không có" || -z "$SELECTED_FS" ]]; then
        print_warn "Phân vùng chưa có filesystem!"
        if confirm "Format $SELECTED_DEV thành ext4? (⚠️ XÓA SẠCH DỮ LIỆU)"; then
            mkfs.ext4 -L "${SHARE_NAME^^}" "$SELECTED_DEV"
            print_ok "Format hoàn tất"
        else
            print_err "Không thể mount phân vùng chưa format."
            exit 1
        fi
    fi
    
    echo ""
    echo -e "  ${BOLD}Xác nhận:${NC}"
    echo -e "  - Device: ${GREEN}${SELECTED_DEV}${NC} (${SELECTED_SIZE})"
    echo -e "  - Mount: ${GREEN}${MOUNT_POINT}${NC}"
    echo -e "  - Samba share: ${GREEN}[${SHARE_NAME^^}]${NC}"
    echo ""
    
    if confirm "Tiến hành mount?"; then
        mkdir -p "$MOUNT_POINT"
        mount "$SELECTED_DEV" "$MOUNT_POINT"
        print_ok "Mounted tại $MOUNT_POINT"
        
        # Thêm vào fstab bằng UUID
        DEV_UUID=$(blkid -s UUID -o value "$SELECTED_DEV")
        if [[ -n "$DEV_UUID" ]] && ! grep -q "$DEV_UUID" /etc/fstab; then
            FSTYPE_ACTUAL=$(blkid -s TYPE -o value "$SELECTED_DEV")
            echo "UUID=$DEV_UUID  $MOUNT_POINT  $FSTYPE_ACTUAL  defaults  0  2" >> /etc/fstab
            print_ok "Đã thêm vào /etc/fstab (UUID: $DEV_UUID)"
        fi
        
        chown -R "$REAL_USER:$REAL_USER" "$MOUNT_POINT"
        chmod -R 775 "$MOUNT_POINT"
        print_ok "Phân quyền cho user '$REAL_USER'"
    else
        print_warn "Đã hủy."
        exit 0
    fi
    ;;

#----------------------------------------------------------------------
# Phương thức 3: Thư mục có sẵn
#----------------------------------------------------------------------
3)
    print_header "📁  Dùng thư mục có sẵn"
    
    read -rp "  → Đường dẫn thư mục: " MOUNT_POINT
    
    if [[ ! -d "$MOUNT_POINT" ]]; then
        if confirm "Thư mục '$MOUNT_POINT' chưa tồn tại. Tạo mới?"; then
            mkdir -p "$MOUNT_POINT"
        else
            exit 1
        fi
    fi
    
    read -rp "  → Tên share (vd: media): " SHARE_NAME
    SHARE_NAME=$(echo "$SHARE_NAME" | tr '[:upper:]' '[:lower:]' | tr ' ' '-' | tr -cd 'a-z0-9-')
    
    chown -R "$REAL_USER:$REAL_USER" "$MOUNT_POINT"
    chmod -R 775 "$MOUNT_POINT"
    print_ok "Phân quyền cho user '$REAL_USER'"
    ;;

#----------------------------------------------------------------------
# Phương thức 4: Xem shares hiện tại
#----------------------------------------------------------------------
4)
    print_header "📋  Samba Shares hiện tại"
    echo ""
    testparm -s 2>/dev/null | grep -A5 "^\[" | grep -v "^\s*$"
    echo ""
    echo -e "  ${BOLD}Dung lượng các mount point:${NC}"
    df -h --output=target,size,used,avail,pcent | grep "/mnt\|/srv\|/home" 2>/dev/null
    exit 0
    ;;

0)
    print_info "Thoát."
    exit 0
    ;;

*)
    print_err "Lựa chọn không hợp lệ!"
    exit 1
    ;;
esac

#==============================================================================
# BƯỚC 4: Cấu hình Samba Share
#==============================================================================
print_header "⚙️  Bước 4: Cấu hình Samba Share"

SAMBA_CONF="/etc/samba/smb.conf"
SHARE_LABEL="${SHARE_NAME^^}"

# Kiểm tra share đã tồn tại chưa
if grep -q "^\[$SHARE_LABEL\]" "$SAMBA_CONF" 2>/dev/null; then
    print_warn "Share [$SHARE_LABEL] đã tồn tại trong smb.conf!"
    if ! confirm "Ghi đè cấu hình cũ?"; then
        print_info "Giữ nguyên cấu hình cũ."
    else
        # Xóa block cũ
        sed -i "/^\[$SHARE_LABEL\]/,/^$/d" "$SAMBA_CONF"
    fi
fi

# Hỏi public hay private
echo ""
echo -e "  ${BOLD}Chế độ truy cập:${NC}"
echo -e "  ${GREEN}1)${NC} Public (ai cũng truy cập, không cần mật khẩu)"
echo -e "  ${GREEN}2)${NC} Private (chỉ user '$REAL_USER', cần mật khẩu)"
read -rp "  → Chọn (1-2): " ACCESS_MODE

if [[ "$ACCESS_MODE" == "1" ]]; then
    cat >> "$SAMBA_CONF" << EOF

[$SHARE_LABEL]
   path = $MOUNT_POINT
   available = yes
   browsable = yes
   public = yes
   writable = yes
   guest ok = yes
   create mask = 0775
   directory mask = 0775
   force user = $REAL_USER
EOF
    print_ok "Share [$SHARE_LABEL] → Public (không cần mật khẩu)"
else
    cat >> "$SAMBA_CONF" << EOF

[$SHARE_LABEL]
   path = $MOUNT_POINT
   available = yes
   valid users = $REAL_USER
   browsable = yes
   public = no
   writable = yes
   read only = no
   create mask = 0775
   directory mask = 0775
EOF
    print_ok "Share [$SHARE_LABEL] → Private (user: $REAL_USER)"
fi

# Restart Samba
print_info "Khởi động lại Samba..."
systemctl restart smbd
systemctl restart nmbd 2>/dev/null || true
print_ok "Samba đã restart"

# Test config
print_info "Kiểm tra cấu hình..."
testparm -s 2>/dev/null | grep -A3 "^\[$SHARE_LABEL\]"

#==============================================================================
# BƯỚC 5: Tổng kết
#==============================================================================
IP_ADDR=$(hostname -I | awk '{print $1}')

print_header "🎉  Hoàn tất! NAS Share đã sẵn sàng"
echo ""
echo -e "  ${BOLD}Thông tin share:${NC}"
echo -e "  ├─ Tên:       ${GREEN}[$SHARE_LABEL]${NC}"
echo -e "  ├─ Path:      ${GREEN}$MOUNT_POINT${NC}"
echo -e "  ├─ Dung lượng: $(df -h "$MOUNT_POINT" --output=avail | tail -1 | tr -d ' ') trống"
echo -e "  └─ User:      ${GREEN}$REAL_USER${NC}"
echo ""
echo -e "  ${BOLD}Truy cập từ:${NC}"
echo -e "  ├─ Windows:   ${CYAN}\\\\\\\\${IP_ADDR}\\\\${SHARE_LABEL}${NC}"
echo -e "  ├─ macOS:     ${CYAN}smb://${IP_ADDR}/${SHARE_LABEL}${NC}"
echo -e "  ├─ Linux:     ${CYAN}smb://${IP_ADDR}/${SHARE_LABEL}${NC}"
echo -e "  └─ Android:   Dùng app File Manager → SMB → ${CYAN}${IP_ADDR}${NC}"
echo ""

# Hiển thị tổng quan tất cả shares
echo -e "  ${BOLD}── Tất cả NAS Shares ──${NC}"
testparm -s 2>/dev/null | grep "^\[" | grep -v "global\|printers\|print" | while read -r line; do
    SNAME=$(echo "$line" | tr -d '[]')
    SPATH=$(testparm -s 2>/dev/null | grep -A10 "^\[$SNAME\]" | grep "path" | awk '{print $3}')
    if [[ -n "$SPATH" ]]; then
        SAVAIL=$(df -h "$SPATH" --output=avail 2>/dev/null | tail -1 | tr -d ' ')
        echo -e "  ${GREEN}[$SNAME]${NC} → $SPATH (trống: $SAVAIL)"
    fi
done
echo ""
