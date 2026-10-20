#!/bin/bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' --data-binary @/root/fathersProject/admin_server_control/tmp_login.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
curl -s http://localhost:8080/api/admin/tables -H "Authorization: Bearer $TOKEN"
