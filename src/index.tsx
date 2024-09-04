import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-local-vpn' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const LocalVpn = NativeModules.LocalVpn
  ? NativeModules.LocalVpn
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export function prepareLocalVPN(): Promise<number> {
  return LocalVpn.prepareLocalVPN();
}
export function connectLocalVPN(): void {
  return LocalVpn.connectLocalVPN();
}
export function disconnectLocalVPN(): void {
  return LocalVpn.disconnectLocalVPN();
}
