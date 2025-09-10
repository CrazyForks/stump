import { Stack } from 'expo-router'
import { Platform } from 'react-native'

export default function Screen() {
	return (
		<Stack
			screenOptions={{
				headerShown: false,
			}}
		>
			<Stack.Screen
				name="index"
				options={{
					headerTitle: '',
					headerShown: Platform.OS === 'ios',
					headerTransparent: true,
					headerBlurEffect: 'regular',
				}}
			/>

			<Stack.Screen
				name="ebook-settings"
				options={{
					presentation: 'modal',
					headerShown: false,
				}}
			/>

			<Stack.Screen
				name="ebook-locations-modal"
				options={{
					presentation: 'modal',
					headerShown: false,
				}}
			/>
		</Stack>
	)
}
