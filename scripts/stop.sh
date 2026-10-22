#!/bin/bash
# Stop the old version. Never fail: on the very first deployment nothing is running yet.
systemctl stop krakow-app || true
exit 0
