#!/bin/sh
set -e

echo "⏳ Waiting for Ganache logs to be ready..."

sleep 2

retries=10
i=1

while [ "$i" -le "$retries" ]; do
  logs=$(docker logs ganache --tail 50 2>&1)

  # Extract address from Available Accounts section (line starting with "(0)")
  address=$(echo "$logs" | grep -m1 '^(0)' | awk '{print $2}')
  # Extract private key from Private Keys section (first occurrence of "(0)" after Private Keys header)
  private_key=$(echo "$logs" | awk '/Private Keys/{p=1} p && /\(0\)/ {print $2; exit}')

  if [ -n "$address" ] && [ -n "$private_key" ]; then
    echo "✅ Found Ganache address and private key."
    break
  fi

  echo "⏳ Attempt $i/$retries: Ganache logs not ready yet..."
  i=$((i + 1))
  sleep 2
done

if [ -z "$address" ] || [ -z "$private_key" ]; then
  echo "❌ Failed to extract Ganache address or private key after $retries attempts."
  exit 1
fi

# Save to .env
ENV_FILE="./.env"

# Remove old entries (use sed -i with backup for Alpine compatibility)
sed -i.bak '/^CONTRACT_ADDRESS=/d' "$ENV_FILE"
sed -i.bak '/^PRIVATE_KEY=/d' "$ENV_FILE"
rm -f "${ENV_FILE}.bak"

echo "CONTRACT_ADDRESS=$address" >> "$ENV_FILE"
echo "PRIVATE_KEY=$private_key" >> "$ENV_FILE"

echo "✅ Ganache Account #0 written to $ENV_FILE"