#!/usr/bin/env node
/**
 * BOOKINGS TABLE STRUCTURE CHECKER
 *
 * This script checks what columns exist in your Supabase bookings table
 * and tells you if you need to run the migration.
 *
 * Usage:
 *   node check-bookings-table.js
 */

const SUPABASE_URL = 'https://rddzycqxtgryrpymivfd.supabase.co';
const ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJkZHp5Y3F4dGdyeXJweW1pdmZkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzE0OTkxMzMsImV4cCI6MjA4NzA3NTEzM30.OYVD3e9A3fqaxw34WL1ccAn-CPtcNvnMya6_8609rAo';

async function checkBookingsTable() {
    console.log('🔍 Checking bookings table structure...\n');

    // Try to fetch one booking to see what columns are returned
    const url = `${SUPABASE_URL}/rest/v1/bookings?limit=1`;

    try {
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'apikey': ANON_KEY,
                'Authorization': `Bearer ${ANON_KEY}`,
                'Accept': 'application/json'
            }
        });

        console.log(`HTTP Status: ${response.status}\n`);

        if (response.status === 200) {
            const data = await response.json();

            if (data.length > 0) {
                const columns = Object.keys(data[0]);
                console.log('✅ Bookings table exists!');
                console.log(`📊 Total columns found: ${columns.length}\n`);
                console.log('📋 Column names:');
                columns.forEach(col => console.log(`   - ${col}`));

                console.log('\n🔍 Checking for enhanced schema columns:');
                const hasFirebaseUid = columns.includes('firebase_uid');
                const hasCustomerId = columns.includes('customer_id');
                const hasServiceId = columns.includes('service_id');
                const hasCompletedAt = columns.includes('completed_at');
                const hasProviderId = columns.includes('provider_id');

                console.log(`   firebase_uid:  ${hasFirebaseUid ? '✅ EXISTS' : '❌ MISSING'}`);
                console.log(`   customer_id:   ${hasCustomerId ? '✅ EXISTS' : '❌ MISSING'}`);
                console.log(`   service_id:    ${hasServiceId ? '✅ EXISTS' : '❌ MISSING'}`);
                console.log(`   completed_at:  ${hasCompletedAt ? '✅ EXISTS' : '❌ MISSING'}`);
                console.log(`   provider_id:   ${hasProviderId ? '✅ EXISTS' : '❌ MISSING'}`);

                console.log('\n📝 Recommendation:');
                if (!hasFirebaseUid) {
                    console.log('   ⚠️  You need to run SUPABASE_BOOKINGS_MIGRATION.sql');
                    console.log('   📱 The current app code will work using provider_name');
                    console.log('   ⚡ After migration, queries will be 8x faster!');
                } else {
                    console.log('   ✅ Enhanced schema already exists!');
                    console.log('   🚀 Your app will use the fast firebase_uid queries');
                }

                console.log('\n📊 Sample booking data:');
                console.log(JSON.stringify(data[0], null, 2));

            } else {
                console.log('⚠️  Bookings table is empty (no data yet)');
                console.log('   The app will work but show "No bookings found"');
            }
        } else if (response.status === 404) {
            console.log('❌ Bookings table not found or not accessible');
            const body = await response.text();
            console.log('Error:', body);
        } else {
            console.log(`⚠️  Unexpected status: ${response.status}`);
            const body = await response.text();
            console.log('Response:', body);
        }
    } catch (error) {
        console.log('❌ Error connecting to Supabase:');
        console.log(error.message);
    }
}

// Run the check
checkBookingsTable();

