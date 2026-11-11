const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');

function load(file) {
    const source = readFileSync(path.join(__dirname, '../src/model', file), 'utf8');
    const { outputText } = ts.transpileModule(source, { compilerOptions: {
        module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020
    } });
    const module = { exports: {} };
    runInNewContext(outputText, { module, exports: module.exports, require: (name) => assert.fail(`Unexpected dependency: ${name}`) });
    return module.exports;
}

const demo = load('demoContact.ts');
const member = load('member.ts');
const seoulDongs = new Map(readFileSync(path.join(__dirname, '../../server/src/main/resources/deliveryfee/seoul-dongs.csv'), 'utf8')
    .trim().split('\n')
    .map((line) => line.split(','))
    .map(([, code, name, district]) => [code, `${district} ${name}`]));
const everyDong = () => true;

test('시연 주소의 행정동은 서버가 배송하는 서울 행정동이다', () => {
    demo.DEMO_PLACES.forEach((place) => {
        assert.ok(seoulDongs.has(place.dongCode), `${place.address}의 행정동 ${place.dongCode}가 목록에 없다`);
    });
});

test('시연 주소는 가입 주소 길이 제한을 넘지 않는다', () => {
    demo.DEMO_PLACES.forEach((place) => {
        assert.ok(place.address.length > 0 && place.address.length <= member.ADDRESS_MAX_LENGTH);
    });
});

test('만든 전화번호는 가입 형식을 통과하고 010-0000으로 시작한다', () => {
    [0, 0.5, 0.999999].forEach((value) => {
        const contact = demo.randomDemoContact(everyDong, () => value);
        assert.ok(member.isPhoneNumberValid(contact.phoneNumber), contact.phoneNumber);
        assert.match(member.formatPhoneNumber(contact.phoneNumber), /^010-0000-[1-9]\d{3}$/);
    });
});

test('난수가 끝값이어도 목록 안의 주소와 그 주소의 행정동을 함께 고른다', () => {
    [0, 0.999999].forEach((value) => {
        const contact = demo.randomDemoContact(everyDong, () => value);
        const place = demo.DEMO_PLACES.find((candidate) => candidate.address === contact.address);
        assert.ok(place);
        assert.equal(contact.dongCode, place.dongCode);
    });
});

test('받아 주는 행정동의 주소만 고른다', () => {
    const allowed = demo.DEMO_PLACES[3].dongCode;
    const contact = demo.randomDemoContact((code) => code === allowed, () => 0.7);
    assert.equal(contact.dongCode, allowed);
    assert.equal(contact.address, demo.DEMO_PLACES[3].address);
});

test('받아 주는 행정동이 없으면 채우지 않는다', () => {
    assert.equal(demo.randomDemoContact(() => false), null);
});
