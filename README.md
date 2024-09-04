# react-native-local-vpn

React Native Android Local VPN Package based on hexene/LocalVPN but its not work properly, after visiting couple website
tcp pipeline broking [related issue](https://github.com/hexene/LocalVPN/issues/18).

after tried fix i brake up proje, and publish after delete it all if someone try to fix can contribute this project.

## Installation

```sh
yarn add react-native-local-vpn
```

## Usage

```js
import LocalVPN from 'react-native-local-vpn';

// ...
LocalVpn.prepareLocalVPN().then((res) => {
  if (res) {
    LocalVpn.connectLocalVPN();
  }
});
```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
