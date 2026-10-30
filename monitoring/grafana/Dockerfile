# 데이터 소스와 대시보드를 이미지에 넣어, 서버를 새로 만들어도 그대로 뜨게 한다
# 대시보드는 데이터 볼륨(/var/lib/grafana) 밖에 둔다. 볼륨 안에 두면 처음 만든 내용이 이미지를 가려 수정이 반영되지 않는다
FROM grafana/grafana:12.0.2
COPY provisioning /etc/grafana/provisioning
COPY dashboards /etc/grafana/dashboards
