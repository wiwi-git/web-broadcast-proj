#!/bin/sh
# 서버에서 나가는 트래픽을 29 Mbit/s로 묶는다.
# 31일 내내 꽉 채워도 약 9.7 TB라서 OCI 무료 송신 한도(월 10 TB)를 넘지 않는다.
# 설치: /usr/local/sbin/egress-cap.sh + /etc/systemd/system/egress-cap.service
RATE=29mbit
IF=$(ip -o -4 route show to default | awk '{print $5}' | head -1)
tc qdisc del dev "$IF" root 2>/dev/null || true
tc qdisc add dev "$IF" root handle 1: htb default 10
tc class add dev "$IF" parent 1: classid 1:10 htb rate $RATE ceil $RATE
tc qdisc add dev "$IF" parent 1:10 fq_codel
