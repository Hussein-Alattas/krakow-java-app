#!/bin/bash
# Start the new version (the service file was created by the EC2 user data).
systemctl daemon-reload
systemctl enable krakow-app
systemctl restart krakow-app
