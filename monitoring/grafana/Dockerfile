# 데이터 소스와 대시보드를 이미지에 넣어, 서버를 새로 만들어도 그대로 뜨게 한다
FROM grafana/grafana:12.0.2
COPY provisioning /etc/grafana/provisioning
COPY dashboards /var/lib/grafana/dashboards
