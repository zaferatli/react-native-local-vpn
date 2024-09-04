
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNLocalVpnSpec.h"

@interface LocalVpn : NSObject <NativeLocalVpnSpec>
#else
#import <React/RCTBridgeModule.h>

@interface LocalVpn : NSObject <RCTBridgeModule>
#endif

@end
