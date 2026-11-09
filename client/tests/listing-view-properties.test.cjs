const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');

const filename = path.join(__dirname, '../src/analytics/listingViewProperties.ts');
const source = readFileSync(filename, 'utf8');
const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
});
const moduleExports = {};
runInNewContext(outputText, { exports: moduleExports }, { filename });
const { listingViewProperties } = moduleExports;

const listing = {
    id: 42,
    widthCm: 149,
    depthCm: 60,
    heightCm: 45,
    category: 'SOFA',
    price: 99_999,
    deliveryFee: 20_000
};

test('조회 이벤트에는 매물 ID와 비교에 필요한 속성이 모두 들어간다', () => {
    assert.deepEqual({ ...listingViewProperties(listing) }, {
        listing_id: 42,
        transport_burden: 'low',
        longest_side_cm: 149,
        category: 'SOFA',
        price: 99_999,
        price_bucket: 'under_100k',
        delivery_fee: 20_000
    });
});

test('가장 긴 변이 149cm면 low, 150cm면 high로 분류한다', () => {
    assert.equal(listingViewProperties(listing).transport_burden, 'low');
    assert.equal(listingViewProperties({ ...listing, widthCm: 150 }).transport_burden, 'high');
});

test('가로, 깊이, 높이 중 가장 큰 값을 긴 변으로 기록한다', () => {
    for (const dimension of ['widthCm', 'depthCm', 'heightCm']) {
        const properties = listingViewProperties({ ...listing, [dimension]: 180 });
        assert.equal(properties.longest_side_cm, 180, dimension);
    }
});

test('상품 가격 10만 원과 30만 원부터 각각 다음 가격대로 분류한다', () => {
    const boundaries = [
        [99_999, 'under_100k'],
        [100_000, '100k_to_300k'],
        [299_999, '100k_to_300k'],
        [300_000, '300k_plus']
    ];
    for (const [price, bucket] of boundaries) {
        assert.equal(listingViewProperties({ ...listing, price }).price_bucket, bucket, String(price));
    }
});
